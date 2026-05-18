package com.catalog.inventory.domain;

public enum ReservationStatus {
    ACTIVE,     // Reservation holds stock — expires_at is in the future
    COMPLETED,  // Checkout succeeded — stock deducted, reservation fulfilled
    EXPIRED,    // expires_at passed — stock released by cleanup job
    CANCELLED   // Explicit cancellation (payment failed, user abandoned)
}

