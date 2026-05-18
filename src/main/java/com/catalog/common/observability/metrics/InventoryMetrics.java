package com.catalog.common.observability.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

@Component
public class InventoryMetrics {

    private final Counter reservationAttemptsTotal;
    private final Counter reservationSuccessTotal;
    private final Counter reservationFailuresTotal;
    private final Timer reservationLatencyTimer;
    private final Counter transferOperationsTotal;
    private final Counter transferFailuresTotal;
    private final Counter bulkImportRowsProcessedTotal;
    private final Counter bulkImportRowsFailedTotal;

    public InventoryMetrics(MeterRegistry registry) {
        reservationAttemptsTotal = Counter.builder("catalog.inventory.reservation.attempts.total")
                .description("Total stock reservation attempts")
                .register(registry);

        reservationSuccessTotal = Counter.builder("catalog.inventory.reservation.success.total")
                .description("Successful stock reservations")
                .register(registry);

        reservationFailuresTotal = Counter.builder("catalog.inventory.reservation.failures.total")
                .description("Failed stock reservations (insufficient stock or optimistic lock exhaustion)")
                .register(registry);

        reservationLatencyTimer = Timer.builder("catalog.inventory.reservation.latency")
                .description("Stock reservation end-to-end latency including retry")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        transferOperationsTotal = Counter.builder("catalog.inventory.transfer.total")
                .description("Warehouse-to-warehouse stock transfer operations")
                .register(registry);

        transferFailuresTotal = Counter.builder("catalog.inventory.transfer.failures.total")
                .description("Failed warehouse transfer operations")
                .register(registry);

        bulkImportRowsProcessedTotal = Counter.builder("catalog.inventory.bulk_import.rows.processed.total")
                .description("Total inventory rows successfully processed in bulk imports")
                .register(registry);

        bulkImportRowsFailedTotal = Counter.builder("catalog.inventory.bulk_import.rows.failed.total")
                .description("Total inventory rows that failed during bulk import")
                .register(registry);
    }

    public void recordReservationAttempt() { reservationAttemptsTotal.increment(); }
    public void recordReservationSuccess(long durationMs) {
        reservationSuccessTotal.increment();
        reservationLatencyTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }
    public void recordReservationFailure(long durationMs) {
        reservationFailuresTotal.increment();
        reservationLatencyTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }
    public void recordTransfer() { transferOperationsTotal.increment(); }
    public void recordTransferFailure() { transferFailuresTotal.increment(); }
    public void recordBulkRow(boolean success) {
        if (success) bulkImportRowsProcessedTotal.increment();
        else bulkImportRowsFailedTotal.increment();
    }

    /**
     * reservation_success_rate: the most important inventory health KPI.
     * If this degrades below ~98%, investigate:
     * - inventory contention (too many concurrent buyers for one SKU)
     * - optimistic lock retry exhaustion (flash sale without pessimistic fallback)
     * - actual stockout (expected if genuinely out of stock)
     */
    public double getReservationSuccessRate() {
        double attempts = reservationAttemptsTotal.count();
        if (attempts == 0) return 1.0;
        return reservationSuccessTotal.count() / attempts;
    }
}

