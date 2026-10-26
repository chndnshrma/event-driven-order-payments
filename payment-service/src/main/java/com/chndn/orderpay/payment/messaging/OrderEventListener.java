package com.chndn.orderpay.payment.messaging;

import com.chndn.orderpay.events.EventTypes;
import com.chndn.orderpay.events.OrderCreatedEvent;
import com.chndn.orderpay.events.Topics;
import com.chndn.orderpay.payment.domain.Payment;
import com.chndn.orderpay.payment.domain.PaymentStatus;
import com.chndn.orderpay.payment.gateway.PaymentGateway;
import com.chndn.orderpay.payment.service.PaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final PaymentService paymentService;
    private final PaymentGateway gateway;
    private final ObjectMapper objectMapper;

    public OrderEventListener(PaymentService paymentService, PaymentGateway gateway, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS)
    public void onMessage(ConsumerRecord<String, String> record) {
        String type = eventType(record);
        if (!EventTypes.ORDER_CREATED.equals(type)) {
            log.warn("Ignoring unknown event type '{}' on {}", type, record.topic());
            return;
        }

        OrderCreatedEvent event;
        try {
            event = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
        } catch (JsonProcessingException e) {
            // Not retryable: goes straight to the dead letter topic (see KafkaConfig).
            throw new IllegalArgumentException("Malformed OrderCreated payload at offset " + record.offset(), e);
        }

        // 1. Local database transaction: find or create the PENDING payment (idempotent per order).
        Payment payment = paymentService.findOrCreateForOrder(event);

        // 2. External call, deliberately OUTSIDE the database transaction. If it fails, this message is
        //    retried; the payment already exists and the gateway call is idempotent on the reference,
        //    so the customer cannot be charged twice.
        if (payment.getStatus() == PaymentStatus.PENDING) {
            gateway.initiateCharge(payment.getGatewayReference(), payment.getAmount(), payment.getCurrency());
        }
    }

    private static String eventType(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(EventTypes.HEADER_EVENT_TYPE);
        if (header == null) {
            throw new IllegalArgumentException("Missing '" + EventTypes.HEADER_EVENT_TYPE + "' header");
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
