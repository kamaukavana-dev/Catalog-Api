package com.catalog.inventory.event;

import java.util.UUID;

public record LowStockEvent(
    UUID inventoryId,
    UUID variantId,
    UUID warehouseId,
    int availableQuantity,
    int reorderLevel
) {}

