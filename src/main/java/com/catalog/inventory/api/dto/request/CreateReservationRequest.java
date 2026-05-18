package com.catalog.inventory.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateReservationRequest(
        @NotNull UUID variantId,
        @NotNull UUID warehouseId,
        @NotNull UUID referenceId,
        @NotNull @Min(1) Integer quantity
) {
}

