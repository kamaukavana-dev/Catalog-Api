package com.catalog.category.event;

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
public class CategoryCacheEvictionListener {

    private final CacheEvictionService evictionService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCategoryMutated(CategoryMutatedEvent event) {
        log.debug("Cache eviction: category id={}", event.id());
        evictionService.evictCategory(event.id(), event.slug());
        evictionService.evictAllProductSearchCaches();
        if (event.oldSlug() != null && !event.oldSlug().equals(event.slug())) {
            evictionService.evictCategory(event.id(), event.oldSlug());
        }
    }
}
