package com.catalog.common.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheEvictionServiceTest {

    @Mock private CacheManager cacheManager;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private Cache cache;

    @InjectMocks private CacheEvictionService evictionService;

    @Test
    void shouldEvictProduct_fromCache() {
        UUID id = UUID.randomUUID();
        String slug = "test-prod";
        when(cacheManager.getCache(CacheNames.PRODUCTS)).thenReturn(cache);

        evictionService.evictProduct(id, slug);

        verify(cache).evict(CacheKeys.productById(id));
        verify(cache).evict(CacheKeys.productBySlug(slug));
    }

    @Test
    void shouldNotFail_whenCacheIsMissing() {
        when(cacheManager.getCache(anyString())).thenReturn(null);
        evictionService.evictBrand(UUID.randomUUID(), "slug");
        verifyNoInteractions(cache);
    }
}
