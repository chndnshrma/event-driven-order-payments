package com.chndn.orderpay.events;

/**
 * Event type names. The type travels in a Kafka header so one topic can carry several event types
 * while the payload stays a plain JSON document.
 */
public final class EventTypes {

    public static final String HEADER_EVENT_TYPE = "eventType";

    public static final String ORDER_CREATED = "OrderCreated";
    public static final String PAYMENT_COMPLETED = "PaymentCompleted";
    public static final String PAYMENT_FAILED = "PaymentFailed";

    private EventTypes() {
    }
}
