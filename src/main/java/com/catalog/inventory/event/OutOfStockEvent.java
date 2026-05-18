package com.catalog.inventory.event;

import java.util.UUID;

public record OutOfStockEvent(
        UUID inventoryId,
        UUID variantId,
        UUID warehouseId
) {
}

