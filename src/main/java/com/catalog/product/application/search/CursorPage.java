package com.catalog.product.application.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.function.Function;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CursorPage<T> {

    private final List<T> content;
    private final int size;
    private final boolean hasMore;
    private final String nextCursor;

    /**
     * Estimated total result count. Never exact for cursor-paginated results.
     * Derived from pg_class.reltuples for large datasets (> 1M rows).
     * Clients MUST NOT rely on this value for arithmetic — use it for display only.
     * Null when estimation is not available or not requested.
     */
    private final Long estimatedTotal;

    /**
     * Whether estimatedTotal is an exact count or an estimate.
     * Drives frontend display: "About 1.2M results" vs "1,248 results"
     */
    private final boolean totalIsExact;

    public static <T> CursorPage<T> of(List<T> allFetched, int requestedSize, Function<T, String> cursorExtractor) {
        boolean hasMore = allFetched.size() > requestedSize;
        List<T> content = hasMore ? allFetched.subList(0, requestedSize) : allFetched;
        String nextCursor = (hasMore && !content.isEmpty()) ? cursorExtractor.apply(content.get(content.size() - 1)) : null;

        return CursorPage.<T>builder()
                .content(content)
                .size(content.size())
                .hasMore(hasMore)
                .nextCursor(nextCursor)
                .build();
    }
}
