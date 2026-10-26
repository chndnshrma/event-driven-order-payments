package com.chndn.orderpay.order.service;

import java.math.BigDecimal;

public record CreateOrderCommand(String customerId, BigDecimal amount, String currency) {
}
