package com.chndn.orderpay.events;

import java.time.Instant;
import java.util.UUID;

/** Published by payment-service when the gateway reported that the charge failed. */
public record PaymentFailedEvent(
        UUID eventId,
        UUID orderId,
        UUID paymentId,
        String reason,
        Instant occurredAt) {
}
