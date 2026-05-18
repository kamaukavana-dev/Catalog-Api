package com.catalog.product.api.dto.request;

import com.catalog.product.domain.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateProductStatusRequest(

        @NotNull(message = "Target status is required")
        ProductStatus targetStatus
) {
}

