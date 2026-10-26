package com.chndn.orderpay.order.domain;

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
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "cancel_reason")
    private String cancelReason;

    /** Optimistic locking: two concurrent updates to the same order cannot silently overwrite each other. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
        // for JPA
    }

    public static Order create(String customerId, BigDecimal amount, String currency, String idempotencyKey) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.amount = amount;
        order.currency = currency;
        order.idempotencyKey = idempotencyKey;
        order.status = OrderStatus.PENDING_PAYMENT;
        order.createdAt = Instant.now();
        order.updatedAt = order.createdAt;
        return order;
    }

    public void markPaid() {
        transitionTo(OrderStatus.PAID);
    }

    public void cancel(String reason) {
        transitionTo(OrderStatus.CANCELLED);
        this.cancelReason = reason;
    }

    private void transitionTo(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Order " + id + " cannot move from " + status + " to " + next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    /** True when the request body matches what this order was created with (used for idempotent replays). */
    public boolean matches(String customerId, BigDecimal amount, String currency) {
        return this.customerId.equals(customerId)
                && this.amount.compareTo(amount) == 0
                && this.currency.equals(currency);
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
