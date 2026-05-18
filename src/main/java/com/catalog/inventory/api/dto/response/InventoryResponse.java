package com.catalog.inventory.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record InventoryResponse(
        UUID id,
        UUID variantId,
        String variantSku,
        UUID warehouseId,
        String warehouseCode,
        String warehouseName,
        int quantity,
        int reservedQuantity,
        int availableQuantity,
        int reorderLevel,
        boolean lowStock,
        boolean outOfStock,
        Instant updatedAt
) {
}

