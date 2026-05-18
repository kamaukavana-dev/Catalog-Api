package com.catalog.inventory.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TransferStockRequest(
    @NotNull UUID sourceInventoryId,
    @NotNull UUID destinationInventoryId,
    @NotNull @Min(1) Integer quantity,
    @Size(max = 500) String reason
) {}

