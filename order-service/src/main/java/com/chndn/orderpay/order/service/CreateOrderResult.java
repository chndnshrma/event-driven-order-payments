package com.chndn.orderpay.order.service;

import com.chndn.orderpay.order.domain.Order;

/** {@code created} is false when the call was a replay of an earlier request with the same idempotency key. */
public record CreateOrderResult(Order order, boolean created) {
}
