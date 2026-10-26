package com.chndn.orderpay.order.api;

import com.chndn.orderpay.order.domain.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        BigDecimal amount,
        String currency,
        String status,
        String cancelReason,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(), order.getCustomerId(), order.getAmount(), order.getCurrency(),
                order.getStatus().name(), order.getCancelReason(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
