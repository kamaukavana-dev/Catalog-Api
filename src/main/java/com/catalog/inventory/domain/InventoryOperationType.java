package com.catalog.inventory.domain;

public enum InventoryOperationType {
    RECEIVE,                // Stock arrived (supplier delivery, purchase order)
    SALE,                   // Unit sold (reservation completed)
    RESERVATION_CREATE,     // Stock reserved during checkout
    RESERVATION_RELEASE,    // Reservation cancelled or expired
    RECONCILIATION,         // Stocktake absolute count correction
    TRANSFER_OUT,           // Stock moved out of this warehouse
    TRANSFER_IN,            // Stock received from another warehouse
    RETURN,                 // Customer return (future)
    DAMAGE,                 // Damaged goods written off (future)
    MANUAL_ADJUSTMENT,      // Admin override
    INITIAL_STOCK           // First stock record creation
}

