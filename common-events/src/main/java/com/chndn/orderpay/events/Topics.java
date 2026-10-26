package com.chndn.orderpay.events;

/** Kafka topic names shared by both services. */
public final class Topics {

    public static final String ORDER_EVENTS = "order-events";
    public static final String PAYMENT_EVENTS = "payment-events";

    /** Suffix used for dead letter topics, e.g. order-events.DLT */
    public static final String DLT_SUFFIX = ".DLT";

    private Topics() {
    }
}
