package com.catalog.product.application.search;

import com.catalog.common.observability.metrics.CacheMetrics;
import com.catalog.product.domain.ProductStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ProductSearchCacheServiceTest {

    @Test
    void shouldFallbackToSearchWhenCachedPayloadIsCorrupt() {
        ProductSearchService searchService = mock(ProductSearchService.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn("{bad-json");

        CacheMetrics cacheMetrics = mock(CacheMetrics.class);
        ProductSearchCacheService cacheService = new ProductSearchCacheService(
                searchService, redisTemplate, cacheMetrics, new ObjectMapper(), 120);

        CursorPage<ProductCardDto> expected = CursorPage.of(
                List.of(new ProductCardDto(
                        UUID.randomUUID(),
                        "Product",
                        "product",
                        "Short",
                        ProductStatus.ACTIVE,
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        true,
                        1,
                        Instant.now()
                )),
                20,
                dto -> "cursor"
        );
        when(searchService.search(any())).thenReturn(expected);

        ProductFilterParams params = new ProductFilterParams(
                null, null, null, null, Set.of(),
                null, null, SortOption.NEWEST, null, 20
        );
        CursorPage<ProductCardDto> actual = cacheService.search(params);

        assertThat(actual).isEqualTo(expected);
        verify(searchService, times(1)).search(any());
        verify(redisTemplate, times(1)).delete(anyString());
    }

    @Test
    void shouldBypassCacheForStockSensitiveQueries() {
        ProductSearchService searchService = mock(ProductSearchService.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        CacheMetrics cacheMetrics = mock(CacheMetrics.class);
        ProductSearchCacheService cacheService = new ProductSearchCacheService(
                searchService, redisTemplate, cacheMetrics, new ObjectMapper(), 120);

        CursorPage<ProductCardDto> expected = CursorPage.of(List.of(), 20, dto -> "cursor");
        when(searchService.search(any())).thenReturn(expected);

        ProductFilterParams params = new ProductFilterParams(
                null, null, null, null, Set.of(),
                true, null, SortOption.NEWEST, null, 20
        );
        CursorPage<ProductCardDto> actual = cacheService.search(params);

        assertThat(actual).isEqualTo(expected);
        verify(redisTemplate, never()).opsForValue();
    }
}
