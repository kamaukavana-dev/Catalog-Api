package com.catalog.product.application.search;

import com.catalog.product.domain.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductCardDto(
        UUID id,
        String name,
        String slug,
        String shortDescription,
        ProductStatus status,
        UUID primaryCategoryId,
        String primaryCategoryName,
        UUID brandId,
        String brandName,
        BigDecimal minEffectivePrice,
        BigDecimal maxBasePrice,
        boolean inStock,
        int variantCount,
        Instant createdAt
) {
}

