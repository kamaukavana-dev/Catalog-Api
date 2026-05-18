package com.catalog.common.cache;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheEvictionService {

    private final CacheManager cacheManager;
    private final RedisTemplate<String, String> redisTemplate;

    public void evictCategory(UUID id, String slug) {
        evict(CacheNames.CATEGORIES, CacheKeys.categoryById(id));
        if (slug != null) {
            evict(CacheNames.CATEGORIES, CacheKeys.categoryBySlug(slug));
        }
        evict(CacheNames.CATEGORIES, CacheKeys.CATEGORY_TREE);
    }

    public void evictBrand(UUID id, String slug) {
        evict(CacheNames.BRANDS, CacheKeys.brandById(id));
        if (slug != null) {
            evict(CacheNames.BRANDS, CacheKeys.brandBySlug(slug));
        }
    }

    public void evictProduct(UUID id, String slug) {
        evict(CacheNames.PRODUCTS, CacheKeys.productById(id));
        if (slug != null) {
            evict(CacheNames.PRODUCTS, CacheKeys.productBySlug(slug));
        }
    }

    public void evictAllProductSearchCaches() {
        ScanOptions options = ScanOptions.scanOptions()
                .match(CacheKeys.PRODUCT_SEARCH_PATTERN)
                .count(100)
                .build();

        List<String> toDelete = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(toDelete::add);
        } catch (Exception ex) {
            log.warn("Failed to scan product search caches: {}", ex.getMessage());
            return;
        }

        if (!toDelete.isEmpty()) {
            redisTemplate.delete(toDelete);
            log.info("Evicted {} product search cache entries.", toDelete.size());
        }
    }

    public void evictAttributeTypes() {
        evict(CacheNames.ATTRIBUTE_TYPES, CacheKeys.ATTRIBUTE_TYPES_ALL);
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "evictFallback")
    private void evict(String cacheName, String key) {
        try {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (Exception ex) {
            log.warn("Cache eviction failed: cache={} key={} error={}", cacheName, key, ex.getMessage());
        }
    }

    private void evictFallback(String cacheName, String key, Exception ex) {
        log.warn("Cache eviction skipped (circuit open): cache={} key={}. TTL will clean up.",
                cacheName, key);
        // Intentional no-op: TTL handles eventual cleanup
    }
}
