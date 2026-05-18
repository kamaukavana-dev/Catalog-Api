package com.catalog.inventory.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdjustStockRequest(
        @NotNull AdjustmentType type,
        @NotNull @Min(1) Integer amount,
        String reason
) {
    public enum AdjustmentType {
        RECEIVE,
        RECONCILE
    }
}

