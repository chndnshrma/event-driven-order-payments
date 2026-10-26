package com.chndn.orderpay.order.service;

import com.chndn.orderpay.events.EventTypes;
import com.chndn.orderpay.events.OrderCreatedEvent;
import com.chndn.orderpay.events.PaymentCompletedEvent;
import com.chndn.orderpay.events.PaymentFailedEvent;
import com.chndn.orderpay.events.Topics;
import com.chndn.orderpay.order.domain.Order;
import com.chndn.orderpay.order.domain.OrderRepository;
import com.chndn.orderpay.order.domain.OrderStatus;
import com.chndn.orderpay.order.messaging.ProcessedEventStore;
import com.chndn.orderpay.order.outbox.OutboxEvent;
import com.chndn.orderpay.order.outbox.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final OutboxRepository outbox;
    private final ProcessedEventStore processedEvents;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public OrderService(OrderRepository orders,
                        OutboxRepository outbox,
                        ProcessedEventStore processedEvents,
                        ObjectMapper objectMapper,
                        PlatformTransactionManager transactionManager) {
        this.orders = orders;
        this.outbox = outbox;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Creates an order exactly once per idempotency key.
     *
     * Not annotated with @Transactional on purpose: when two identical requests race, the loser hits the
     * unique constraint and its transaction is poisoned. We let that transaction end, then read the
     * winner's order in a fresh one.
     */
    public CreateOrderResult createOrder(String idempotencyKey, CreateOrderCommand cmd) {
        var existing = orders.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), idempotencyKey, cmd);
        }

        try {
            Order created = tx.execute(status -> insertOrderAndOutbox(idempotencyKey, cmd));
            return new CreateOrderResult(created, true);
        } catch (DataIntegrityViolationException raceLost) {
            Order winner = orders.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> raceLost);
            return replay(winner, idempotencyKey, cmd);
        }
    }

    private CreateOrderResult replay(Order existing, String key, CreateOrderCommand cmd) {
        if (!existing.matches(cmd.customerId(), cmd.amount(), cmd.currency())) {
            throw new IdempotencyConflictException(key);
        }
        return new CreateOrderResult(existing, false);
    }

    private Order insertOrderAndOutbox(String idempotencyKey, CreateOrderCommand cmd) {
        Order order = orders.saveAndFlush(
                Order.create(cmd.customerId(), cmd.amount(), cmd.currency(), idempotencyKey));

        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(), order.getId(), order.getCustomerId(),
                order.getAmount(), order.getCurrency(), Instant.now());

        // Same transaction as the order insert: this is the transactional outbox.
        outbox.save(OutboxEvent.create(order.getId(), Topics.ORDER_EVENTS, EventTypes.ORDER_CREATED, toJson(event)));
        return order;
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        if (!processedEvents.markProcessed(event.eventId())) {
            log.info("Duplicate PaymentCompleted event {} ignored", event.eventId());
            return;
        }
        Order order = orders.findById(event.orderId())
                .orElseThrow(() -> new OrderNotFoundException(event.orderId()));

        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }
        if (!order.getStatus().canTransitionTo(OrderStatus.PAID)) {
            // Money was taken for an order that is already cancelled. This needs a refund flow, which is a
            // deliberate next step; for now it is surfaced loudly instead of being hidden.
            log.error("Payment {} completed for order {} that is already {} - manual refund review needed",
                    event.paymentId(), order.getId(), order.getStatus());
            return;
        }
        order.markPaid();
    }

    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (!processedEvents.markProcessed(event.eventId())) {
            log.info("Duplicate PaymentFailed event {} ignored", event.eventId());
            return;
        }
        Order order = orders.findById(event.orderId())
                .orElseThrow(() -> new OrderNotFoundException(event.orderId()));

        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            log.warn("PaymentFailed for order {} ignored, order is already {}", order.getId(), order.getStatus());
            return;
        }
        // Compensating action of the saga: payment failed, so the order is cancelled.
        order.cancel(event.reason());
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize event", e);
        }
    }
}
