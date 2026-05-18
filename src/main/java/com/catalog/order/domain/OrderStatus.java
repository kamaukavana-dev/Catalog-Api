package com.catalog.order.domain;

public enum OrderStatus {
    PENDING,
    PAID,
    CANCELLED,
    SHIPPED,
    DELIVERED,
    RETURNED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == PAID || target == CANCELLED;
            case PAID -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED;
            case DELIVERED -> target == RETURNED;
            case CANCELLED, RETURNED -> false;
        };
    }

    public void assertCanTransitionTo(OrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Invalid order status transition: " + this + " -> " + target);
        }
    }
}

