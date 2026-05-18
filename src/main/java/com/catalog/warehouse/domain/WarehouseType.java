package com.catalog.warehouse.domain;

public enum WarehouseType {
    MAIN,               // Primary distribution center
    RETAIL,             // Physical retail location with stock
    DARK_STORE,         // Fulfillment-only, no public access
    FULFILLMENT_CENTER, // Third-party logistics partner
    PICKUP_POINT        // Click-and-collect location
}

