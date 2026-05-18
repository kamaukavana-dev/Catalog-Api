package com.catalog.common.response;

import java.util.List;

public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    boolean totalExact,   // false when using pg_class estimate
    int totalPages,
    boolean last,
    boolean first
) {
    public static <T> PagedResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                true,
                page.getTotalPages(),
                page.isLast(),
                page.isFirst()
        );
    }
}
