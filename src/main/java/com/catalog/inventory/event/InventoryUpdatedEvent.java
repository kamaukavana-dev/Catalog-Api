package com.catalog.inventory.event;

import java.util.UUID;

public record InventoryUpdatedEvent(
        UUID variantId,
        UUID productId
) {
}

