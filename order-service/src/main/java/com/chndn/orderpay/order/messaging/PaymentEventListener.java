package com.chndn.orderpay.order.messaging;

import com.chndn.orderpay.events.EventTypes;
import com.chndn.orderpay.events.PaymentCompletedEvent;
import com.chndn.orderpay.events.PaymentFailedEvent;
import com.chndn.orderpay.events.Topics;
import com.chndn.orderpay.order.service.OrderService;
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
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public PaymentEventListener(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS)
    public void onMessage(ConsumerRecord<String, String> record) {
        String type = eventType(record);
        try {
            switch (type) {
                case EventTypes.PAYMENT_COMPLETED ->
                        orderService.onPaymentCompleted(objectMapper.readValue(record.value(), PaymentCompletedEvent.class));
                case EventTypes.PAYMENT_FAILED ->
                        orderService.onPaymentFailed(objectMapper.readValue(record.value(), PaymentFailedEvent.class));
                default -> log.warn("Ignoring unknown event type '{}' on {}", type, record.topic());
            }
        } catch (JsonProcessingException e) {
            // Not retryable: goes straight to the dead letter topic (see KafkaConfig).
            throw new IllegalArgumentException("Malformed " + type + " payload at offset " + record.offset(), e);
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
