package com.catalog.common.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CacheMetrics {

    // Per-cache-namespace counters: hit_rate computed as hits/total in Grafana
    private final Counter categoryHits;
    private final Counter categoryMisses;
    private final Counter productHits;
    private final Counter productMisses;
    private final Counter searchHits;
    private final Counter searchMisses;

    public CacheMetrics(MeterRegistry registry) {
        categoryHits   = buildCounter(registry, "categories", "hit");
        categoryMisses = buildCounter(registry, "categories", "miss");
        productHits    = buildCounter(registry, "products", "hit");
        productMisses  = buildCounter(registry, "products", "miss");
        searchHits     = buildCounter(registry, "product-search", "hit");
        searchMisses   = buildCounter(registry, "product-search", "miss");
    }

    private Counter buildCounter(MeterRegistry registry, String cache, String outcome) {
        return Counter.builder("catalog.cache.lookups.total")
                .description("Cache lookup outcomes by cache and result")
                .tag("cache", cache)
                .tag("outcome", outcome)
                .register(registry);
    }

    public void recordCategoryHit() { categoryHits.increment(); }
    public void recordCategoryMiss() { categoryMisses.increment(); }
    public void recordProductHit() { productHits.increment(); }
    public void recordProductMiss() { productMisses.increment(); }
    public void recordSearchHit() { searchHits.increment(); }
    public void recordSearchMiss() { searchMisses.increment(); }
}

