package com.catalog.inventory.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateInventoryRequest(
        @NotNull UUID variantId,
        @NotNull UUID warehouseId,
        @Min(0) int initialQuantity,
        @Min(0) int reorderLevel
) {
}

