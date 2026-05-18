package com.catalog.common.observability.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Catalog search metrics.
 *
 * Metric types chosen deliberately:
 * - Timer: latency distribution (p50/p95/p99 from single metric)
 * - Counter: cumulative event count (monotonically increasing)
 * - Gauge: current state snapshot (changes up and down)
 *
 * Tag cardinality rule: tags must have BOUNDED value sets.
 * Never use productId, searchQuery, or userId as tags — cardinality explosion
 * would create millions of time series and crash Prometheus.
 */
@Slf4j
@Component
public class SearchMetrics {

    // Timer: measures duration AND count. Percentiles configured in application.yml.
    private final Timer searchLatencyTimer;

    // Counters: cumulative totals — compute rate() and success_rate in Grafana
    private final Counter searchRequestsTotal;
    private final Counter searchZeroResultsTotal;
    private final Counter searchErrorsTotal;

    public SearchMetrics(MeterRegistry registry) {
        // Timer auto-registers histogram buckets for percentile calculation
        searchLatencyTimer = Timer.builder("catalog.search.latency")
                .description("Product search end-to-end latency")
                .tag("component", "search")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);

        searchRequestsTotal = Counter.builder("catalog.search.requests.total")
                .description("Total product search requests executed")
                .register(registry);

        searchZeroResultsTotal = Counter.builder("catalog.search.zero_results.total")
                .description("Search requests returning zero results. " +
                             "High rate indicates catalog quality or indexing issues.")
                .register(registry);

        searchErrorsTotal = Counter.builder("catalog.search.errors.total")
                .description("Search requests that failed with an error")
                .register(registry);
    }

    /**
     * Record a completed search operation.
     *
     * @param durationMs actual query duration in milliseconds
     * @param resultCount number of results returned
     * @param hasAttributeFilter whether attribute filtering was applied
     */
    public void recordSearch(long durationMs, int resultCount, boolean hasAttributeFilter) {
        searchRequestsTotal.increment();
        searchLatencyTimer.record(durationMs, TimeUnit.MILLISECONDS);

        if (resultCount == 0) {
            searchZeroResultsTotal.increment();
        }
    }

    public void recordSearchError() {
        searchRequestsTotal.increment();
        searchErrorsTotal.increment();
    }

    /**
     * Compute and log zero result rate for monitoring.
     * The actual rate is computed by Prometheus: rate(zero_results[5m]) / rate(requests[5m])
     */
    public double getZeroResultRate() {
        double requests = searchRequestsTotal.count();
        if (requests == 0) return 0;
        return searchZeroResultsTotal.count() / requests;
    }
}

