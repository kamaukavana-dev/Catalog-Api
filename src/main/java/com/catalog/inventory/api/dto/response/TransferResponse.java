package com.catalog.inventory.api.dto.response;

import java.util.UUID;

public record TransferResponse(
    UUID transferReferenceId,
    UUID sourceInventoryId,
    int sourceAvailableQuantityAfter,
    UUID destinationInventoryId,
    int destinationAvailableQuantityAfter,
    int quantityTransferred
) {}

