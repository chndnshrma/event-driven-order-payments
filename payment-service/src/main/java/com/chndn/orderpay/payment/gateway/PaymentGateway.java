package com.chndn.orderpay.payment.gateway;

import java.math.BigDecimal;

/**
 * Port to an external payment gateway. A real implementation (Stripe, Razorpay...) would call the gateway's
 * API here; the outcome of the charge comes back later as a webhook, not as the return value.
 */
public interface PaymentGateway {

    /**
     * Asks the gateway to charge. Must be idempotent for the same {@code reference}: the caller may retry
     * after a crash, and a customer must never be charged twice.
     */
    void initiateCharge(String reference, BigDecimal amount, String currency);
}
