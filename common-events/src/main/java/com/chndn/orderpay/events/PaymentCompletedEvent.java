package com.chndn.orderpay.events;

import java.time.Instant;
import java.util.UUID;

/** Published by payment-service when the gateway confirmed the charge. */
public record PaymentCompletedEvent(
        UUID eventId,
        UUID orderId,
        UUID paymentId,
        Instant occurredAt) {
}
