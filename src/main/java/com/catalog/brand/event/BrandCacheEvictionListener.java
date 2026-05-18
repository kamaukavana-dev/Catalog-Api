package com.catalog.brand.event;

import com.catalog.common.cache.CacheEvictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrandCacheEvictionListener {

    private final CacheEvictionService evictionService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBrandMutated(BrandMutatedEvent event) {
        log.debug("Cache eviction: brand id={}", event.id());
        evictionService.evictBrand(event.id(), event.slug());
        evictionService.evictAllProductSearchCaches();
        if (event.oldSlug() != null && !event.oldSlug().equals(event.slug())) {
            evictionService.evictBrand(event.id(), event.oldSlug());
        }
    }
}
