package com.chndn.orderpay.payment.service;

import com.chndn.orderpay.events.EventTypes;
import com.chndn.orderpay.events.OrderCreatedEvent;
import com.chndn.orderpay.events.PaymentCompletedEvent;
import com.chndn.orderpay.events.PaymentFailedEvent;
import com.chndn.orderpay.events.Topics;
import com.chndn.orderpay.payment.domain.Payment;
import com.chndn.orderpay.payment.domain.PaymentRepository;
import com.chndn.orderpay.payment.domain.PaymentStatus;
import com.chndn.orderpay.payment.outbox.OutboxEvent;
import com.chndn.orderpay.payment.outbox.OutboxRepository;
import com.chndn.orderpay.payment.webhook.GatewayWebhook;
import com.chndn.orderpay.payment.webhook.ProcessedWebhookStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final OutboxRepository outbox;
    private final ProcessedWebhookStore processedWebhooks;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository payments,
                          OutboxRepository outbox,
                          ProcessedWebhookStore processedWebhooks,
                          ObjectMapper objectMapper) {
        this.payments = payments;
        this.outbox = outbox;
        this.processedWebhooks = processedWebhooks;
        this.objectMapper = objectMapper;
    }

    /**
     * Idempotent by construction: there is at most one payment per order (unique constraint on order_id),
     * so a redelivered OrderCreated event just finds the payment that already exists.
     */
    @Transactional
    public Payment findOrCreateForOrder(OrderCreatedEvent event) {
        return payments.findByOrderId(event.orderId())
                .orElseGet(() -> payments.saveAndFlush(
                        Payment.createPending(event.orderId(), event.amount(), event.currency())));
    }

    /**
     * Applies a verified gateway webhook. Duplicate deliveries (same gateway event id) are ignored.
     * The payment update and the outbox row are written in one transaction.
     */
    @Transactional
    public void handleWebhook(GatewayWebhook webhook) {
        if (!processedWebhooks.markProcessed(webhook.eventId())) {
            log.info("Duplicate webhook {} ignored", webhook.eventId());
            return;
        }

        // If the payment is unknown the exception rolls back the "processed" marker as well,
        // so the gateway's retry will be handled normally once the payment exists.
        Payment payment = payments.findByGatewayReference(webhook.reference())
                .orElseThrow(() -> new PaymentNotFoundException(webhook.reference()));

        switch (webhook.type()) {
            case GatewayWebhook.CHARGE_SUCCEEDED -> {
                if (!payment.getStatus().canTransitionTo(PaymentStatus.COMPLETED)) {
                    log.warn("charge.succeeded for payment {} ignored, it is already {}", payment.getId(), payment.getStatus());
                    return;
                }
                payment.complete();
                PaymentCompletedEvent event = new PaymentCompletedEvent(
                        UUID.randomUUID(), payment.getOrderId(), payment.getId(), Instant.now());
                outbox.save(OutboxEvent.create(
                        payment.getOrderId(), Topics.PAYMENT_EVENTS, EventTypes.PAYMENT_COMPLETED, toJson(event)));
            }
            case GatewayWebhook.CHARGE_FAILED -> {
                if (!payment.getStatus().canTransitionTo(PaymentStatus.FAILED)) {
                    log.warn("charge.failed for payment {} ignored, it is already {}", payment.getId(), payment.getStatus());
                    return;
                }
                payment.fail(webhook.reason());
                PaymentFailedEvent event = new PaymentFailedEvent(
                        UUID.randomUUID(), payment.getOrderId(), payment.getId(), webhook.reason(), Instant.now());
                outbox.save(OutboxEvent.create(
                        payment.getOrderId(), Topics.PAYMENT_EVENTS, EventTypes.PAYMENT_FAILED, toJson(event)));
            }
            default -> log.warn("Ignoring unknown webhook type '{}'", webhook.type());
        }
    }

    @Transactional(readOnly = true)
    public Payment getByOrderId(UUID orderId) {
        return payments.findByOrderId(orderId).orElseThrow(() -> new PaymentNotFoundException("order " + orderId));
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize event", e);
        }
    }
}
