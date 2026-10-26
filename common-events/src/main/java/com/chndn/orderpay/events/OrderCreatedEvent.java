package com.chndn.orderpay.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by order-service when an order has been accepted and is waiting for payment.
 * {@code eventId} is the key consumers use to ignore duplicate deliveries.
 */
public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        Instant occurredAt) {
}
