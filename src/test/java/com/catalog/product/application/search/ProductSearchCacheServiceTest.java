package com.catalog.product.application.search;

import com.catalog.common.observability.metrics.CacheMetrics;
import com.catalog.product.domain.ProductStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
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

    @Test
    void cacheMissDelegatesToSearchAndStoresFullyKeyedResult() {
        ProductSearchService searchService = mock(ProductSearchService.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);
        CacheMetrics cacheMetrics = mock(CacheMetrics.class);
        ProductSearchCacheService cacheService = new ProductSearchCacheService(
                searchService, redisTemplate, cacheMetrics, new ObjectMapper(), 120);

        UUID cardId = UUID.randomUUID();
        CursorPage<ProductCardDto> expected = CursorPage.of(List.of(sampleCard(cardId)), 20, dto -> "cursor");
        when(searchService.search(any())).thenReturn(expected);

        // Fully-populated, cacheable params (no cursor, inStock not TRUE) so that every
        // optional branch in buildCacheKey is taken: category, brand, min/max price,
        // attribute ids, and non-blank search text.
        ProductFilterParams params = new ProductFilterParams(
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("5.00"), new BigDecimal("50.00"),
                Set.of(UUID.randomUUID(), UUID.randomUUID()),
                null, "Blue Shirt", SortOption.PRICE_LOW, null, 24);

        CursorPage<ProductCardDto> result = cacheService.search(params);

        assertThat(result).isSameAs(expected);
        verify(cacheMetrics).recordSearchMiss();
        verify(cacheMetrics, never()).recordSearchHit();

        // On a miss the freshly computed page is serialized and stored under the derived key.
        ArgumentCaptor<String> storedJson = ArgumentCaptor.forClass(String.class);
        verify(ops).set(anyString(), storedJson.capture(), any(Duration.class));
        assertThat(storedJson.getValue()).contains(cardId.toString());
    }

    @Test
    void redisReadFailureFallsBackToSearchWithoutError() {
        ProductSearchService searchService = mock(ProductSearchService.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenThrow(new RuntimeException("redis down"));
        CacheMetrics cacheMetrics = mock(CacheMetrics.class);
        ProductSearchCacheService cacheService = new ProductSearchCacheService(
                searchService, redisTemplate, cacheMetrics, new ObjectMapper(), 120);

        CursorPage<ProductCardDto> expected = CursorPage.of(List.of(sampleCard(UUID.randomUUID())), 20, dto -> "c");
        when(searchService.search(any())).thenReturn(expected);

        ProductFilterParams params = new ProductFilterParams(
                null, null, null, null, Set.of(), null, "  ", SortOption.NEWEST, null, 20);

        CursorPage<ProductCardDto> actual = cacheService.search(params);

        // A dead Redis must never surface to the caller: treat it as a miss and serve live.
        assertThat(actual).isSameAs(expected);
        verify(searchService, times(1)).search(any());
    }

    @Test
    void cursorPagesAreNeverCached() {
        ProductSearchService searchService = mock(ProductSearchService.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        CacheMetrics cacheMetrics = mock(CacheMetrics.class);
        ProductSearchCacheService cacheService = new ProductSearchCacheService(
                searchService, redisTemplate, cacheMetrics, new ObjectMapper(), 120);

        CursorPage<ProductCardDto> expected = CursorPage.of(List.of(), 20, dto -> "c");
        when(searchService.search(any())).thenReturn(expected);

        // A non-null cursor means we are deep-paginating: results are not cacheable.
        ProductFilterParams params = new ProductFilterParams(
                null, null, null, null, Set.of(), null, null, SortOption.NEWEST, "eyJvIjoxfQ==", 20);

        CursorPage<ProductCardDto> actual = cacheService.search(params);

        assertThat(actual).isSameAs(expected);
        verify(redisTemplate, never()).opsForValue();
        verify(cacheMetrics, never()).recordSearchHit();
        verify(cacheMetrics, never()).recordSearchMiss();
    }

    private static ProductCardDto sampleCard(UUID id) {
        return new ProductCardDto(
                id, "Product", "product", "Short", ProductStatus.ACTIVE,
                null, null, null, null, BigDecimal.TEN, BigDecimal.TEN, true, 1, Instant.now());
    }
}
