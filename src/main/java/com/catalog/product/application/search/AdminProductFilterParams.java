package com.catalog.product.application.search;

import com.catalog.product.domain.ProductStatus;

import java.util.Set;
import java.util.UUID;

public record AdminProductFilterParams(
        Set<ProductStatus> statuses,
        UUID categoryId,
        UUID brandId,
        String search,
        SortOption sort,
        int page,
        int size
) {
    public AdminProductFilterParams {
        if (sort == null) {
            sort = SortOption.NEWEST;
        }
        if (size <= 0 || size > 200) {
            size = 50;
        }
        if (page < 0) {
            page = 0;
        }
    }
}

