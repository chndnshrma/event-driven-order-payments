package com.chndn.orderpay.order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void pendingOrderCanBePaidOrCancelled() {
        assertTrue(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PAID));
        assertTrue(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.CANCELLED));
    }

    @Test
    void terminalStatesCannotChange() {
        for (OrderStatus next : OrderStatus.values()) {
            assertFalse(OrderStatus.PAID.canTransitionTo(next));
            assertFalse(OrderStatus.CANCELLED.canTransitionTo(next));
        }
    }

    @Test
    void paidOrderCannotBeCancelledByALateFailureEvent() {
        Order order = Order.create("c-1", new BigDecimal("10.00"), "USD", "key-1");
        order.markPaid();

        assertThrows(IllegalStateException.class, () -> order.cancel("late failure"));
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    void cancelRecordsReason() {
        Order order = Order.create("c-1", new BigDecimal("10.00"), "USD", "key-2");
        order.cancel("card declined");

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals("card declined", order.getCancelReason());
    }

    @Test
    void matchesComparesAmountNumerically() {
        Order order = Order.create("c-1", new BigDecimal("10.00"), "USD", "key-3");

        assertTrue(order.matches("c-1", new BigDecimal("10.0"), "USD"));
        assertFalse(order.matches("c-1", new BigDecimal("11.00"), "USD"));
        assertFalse(order.matches("c-2", new BigDecimal("10.00"), "USD"));
    }
}
