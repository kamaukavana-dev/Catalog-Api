package com.catalog.common.exception;

import java.util.UUID;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(UUID variantId, UUID warehouseId,
                                       int requested, int available) {
        super(String.format(
            "Insufficient stock for variant %s at warehouse %s. " +
            "Requested: %d, Available: %d",
            variantId, warehouseId, requested, available));
    }
}

