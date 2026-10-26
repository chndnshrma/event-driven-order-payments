package com.chndn.orderpay.payment.domain;

/**
 * PENDING --charge succeeded--> COMPLETED
 * PENDING --charge failed-----> FAILED
 *
 * COMPLETED and FAILED are terminal, so a late or replayed webhook cannot flip a finished payment.
 */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED;

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case PENDING -> next == COMPLETED || next == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }
}
