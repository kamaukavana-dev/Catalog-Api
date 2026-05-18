package com.catalog.product.api.dto.response;

import com.catalog.product.domain.ProductStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String slug,
        String shortDescription,
        String description,
        ProductStatus status,
        UUID primaryCategoryId,
        String primaryCategoryName,
        UUID brandId,
        String brandName,
        String metaTitle,
        String metaDescription,
        Set<UUID> secondaryCategoryIds,
        Instant createdAt,
        Instant updatedAt
) {
}

