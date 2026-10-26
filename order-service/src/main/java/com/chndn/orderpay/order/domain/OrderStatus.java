package com.chndn.orderpay.order.domain;

/**
 * Order lifecycle as an explicit state machine.
 *
 * <pre>
 *   PENDING_PAYMENT --payment completed--> PAID
 *   PENDING_PAYMENT --payment failed-----> CANCELLED   (the saga's compensating action)
 * </pre>
 *
 * PAID and CANCELLED are terminal. Every status change goes through {@link #canTransitionTo}, so an
 * out-of-order or duplicate event can never move an order into an invalid state.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING_PAYMENT -> next == PAID || next == CANCELLED;
            case PAID, CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return this != PENDING_PAYMENT;
    }
}
