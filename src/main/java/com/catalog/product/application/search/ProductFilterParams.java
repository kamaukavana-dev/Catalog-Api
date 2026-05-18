package com.catalog.product.application.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record ProductFilterParams(
        UUID categoryId,
        UUID brandId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Set<UUID> attributeValueIds,
        Boolean inStock,
        @Size(max = 200) String search,
        SortOption sort,
        String cursor,
        @Min(1) @Max(100) int pageSize
) {
    public ProductFilterParams {
        if (sort == null) {
            sort = SortOption.NEWEST;
        }
        if (pageSize <= 0) {
            pageSize = 20;
        }
        if (attributeValueIds == null) {
            attributeValueIds = Set.of();
        }
    }
}

