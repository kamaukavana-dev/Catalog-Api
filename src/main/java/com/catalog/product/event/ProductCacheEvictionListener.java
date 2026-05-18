package com.catalog.product.event;

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
public class ProductCacheEvictionListener {

    private final CacheEvictionService evictionService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductMutated(ProductMutatedEvent event) {
        log.debug("Cache eviction: product id={} visibilityChanged={}", event.id(), event.visibilityChanged());
        evictionService.evictProduct(event.id(), event.slug());
        if (event.oldSlug() != null && !event.oldSlug().equals(event.slug())) {
            evictionService.evictProduct(event.id(), event.oldSlug());
        }
        if (event.visibilityChanged()) {
            evictionService.evictAllProductSearchCaches();
        }
    }
}

