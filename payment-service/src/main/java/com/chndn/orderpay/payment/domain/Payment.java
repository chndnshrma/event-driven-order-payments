package com.chndn.orderpay.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    /** Our own charge reference. Also used as the idempotency key when asking the gateway to charge. */
    @Column(name = "gateway_reference", nullable = false, length = 100, updatable = false)
    private String gatewayReference;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
        // for JPA
    }

    public static Payment createPending(UUID orderId, BigDecimal amount, String currency) {
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.orderId = orderId;
        payment.amount = amount;
        payment.currency = currency;
        payment.status = PaymentStatus.PENDING;
        payment.gatewayReference = "ch_" + payment.id;
        payment.createdAt = Instant.now();
        payment.updatedAt = payment.createdAt;
        return payment;
    }

    public void complete() {
        transitionTo(PaymentStatus.COMPLETED);
    }

    public void fail(String reason) {
        transitionTo(PaymentStatus.FAILED);
        this.failureReason = reason;
    }

    private void transitionTo(PaymentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Payment " + id + " cannot move from " + status + " to " + next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getGatewayReference() {
        return gatewayReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
