package com.catalog.product.application.search;

import com.catalog.common.cache.CacheKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductSearchCacheService {

    private final ProductSearchService searchService;
    private final RedisTemplate<String, String> redisTemplate;
    private final com.catalog.common.observability.metrics.CacheMetrics cacheMetrics;
    private final long searchTtlSeconds;
    private final ObjectMapper mapper;

    public ProductSearchCacheService(
            ProductSearchService searchService,
            RedisTemplate<String, String> redisTemplate,
            com.catalog.common.observability.metrics.CacheMetrics cacheMetrics,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${catalog.cache.product-search-ttl-seconds:120}") long searchTtlSeconds) {
        this.searchService = searchService;
        this.redisTemplate = redisTemplate;
        this.cacheMetrics = cacheMetrics;
        this.searchTtlSeconds = searchTtlSeconds;
        this.mapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    public CursorPage<ProductCardDto> search(ProductFilterParams params) {
        boolean isCacheable = isCacheable(params);
        String cacheKey = isCacheable ? buildCacheKey(params) : null;

        if (isCacheable && cacheKey != null) {
            String cached = getCached(cacheKey);
            if (cached != null) {
                CursorPage<ProductCardDto> cachedPage = deserializePage(cached);
                if (cachedPage != null) {
                    cacheMetrics.recordSearchHit();
                    return cachedPage;
                }
                evictCorruptCacheEntry(cacheKey);
            }
            cacheMetrics.recordSearchMiss();
        }

        CursorPage<ProductCardDto> result = searchService.search(params);
        if (isCacheable && cacheKey != null && result != null) {
            cache(cacheKey, result);
        }
        return result;
    }

    private String buildCacheKey(ProductFilterParams params) {
        Map<String, Object> normalized = new TreeMap<>();
        if (params.categoryId() != null) normalized.put("cat", params.categoryId().toString());
        if (params.brandId() != null) normalized.put("brand", params.brandId().toString());
        if (params.minPrice() != null) normalized.put("minP", params.minPrice().toPlainString());
        if (params.maxPrice() != null) normalized.put("maxP", params.maxPrice().toPlainString());
        if (params.inStock() != null) normalized.put("stock", params.inStock());
        if (params.search() != null && !params.search().isBlank()) normalized.put("q", params.search().trim().toLowerCase());
        if (!params.attributeValueIds().isEmpty()) {
            normalized.put("attrs", params.attributeValueIds().stream().map(UUID::toString).sorted().collect(Collectors.toList()));
        }
        normalized.put("sort", params.sort().name());
        normalized.put("size", params.pageSize());

        try {
            String json = mapper.writeValueAsString(normalized);
            return CacheKeys.productSearch(sha256(json));
        } catch (JsonProcessingException e) {
            log.warn("Failed to build search cache key: {}", e.getMessage());
            return null;
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String getCached(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis GET failed for search key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private void evictCorruptCacheEntry(String key) {
        if (key == null) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            log.warn("Failed to evict corrupt search cache key={}: {}", key, ex.getMessage());
        }
    }

    private void cache(String key, CursorPage<ProductCardDto> result) {
        try {
            String json = mapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(searchTtlSeconds));
        } catch (Exception e) {
            log.warn("Redis SET failed for search cache key={}: {}", key, e.getMessage());
        }
    }

    private CursorPage<ProductCardDto> deserializePage(String json) {
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructParametricType(CursorPage.class, ProductCardDto.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached search result: {}", e.getMessage());
            return null;
        }
    }

    private boolean isCacheable(ProductFilterParams params) {
        if (params.cursor() != null) {
            return false;
        }
        return !Boolean.TRUE.equals(params.inStock());
    }
}
