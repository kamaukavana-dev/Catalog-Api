package com.catalog.product.application.search;

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
        @Min(1) int pageSize
) {
    private static final int MAX_PAGE_SIZE = 100;

    public ProductFilterParams {
        if (sort == null) {
            sort = SortOption.NEWEST;
        }
        if (pageSize <= 0) {
            pageSize = 20;
        } else if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
        if (attributeValueIds == null) {
            attributeValueIds = Set.of();
        }
    }
}
