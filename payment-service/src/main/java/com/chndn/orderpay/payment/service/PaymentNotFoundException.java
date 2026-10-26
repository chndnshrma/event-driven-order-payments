package com.chndn.orderpay.payment.service;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String what) {
        super("Payment not found: " + what);
    }
}
