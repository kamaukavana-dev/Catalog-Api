package com.catalog.product.api.dto.response;

import com.catalog.product.domain.ProductStatus;

import java.time.Instant;
import java.util.UUID;

public record ProductSummaryResponse(
        UUID id,
        String name,
        String slug,
        String shortDescription,
        ProductStatus status,
        UUID primaryCategoryId,
        String primaryCategoryName,
        UUID brandId,
        String brandName,
        Instant createdAt
) {
}

