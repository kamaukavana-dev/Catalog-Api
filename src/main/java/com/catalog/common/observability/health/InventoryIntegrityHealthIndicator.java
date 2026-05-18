package com.catalog.common.observability.health;

import com.catalog.inventory.infrastructure.InventoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Slf4j
@Component("inventory-integrity")
public class InventoryIntegrityHealthIndicator implements HealthIndicator {

    private final InventoryRepository inventoryRepository;
    private final long minimumInventoryRecordCount;

    public InventoryIntegrityHealthIndicator(
            InventoryRepository inventoryRepository,
            @Value("${catalog.health.inventory.minimum-record-count:1}") long minimumInventoryRecordCount) {
        this.inventoryRepository = inventoryRepository;
        this.minimumInventoryRecordCount = minimumInventoryRecordCount;
    }

    @Override
    public Health health() {
        try {
            long count = inventoryRepository.count();

            if (count == 0 && minimumInventoryRecordCount > 0) {
                // Zero records after system has been used = data loss or migration failure.
                // Note: this is UP on first boot before any inventory is created.
                return Health.status(CatalogHealthStatus.DEGRADED)
                        .withDetail("inventory_records", 0)
                        .withDetail("reason", "No inventory records found. " +
                                "Possible data loss or failed migration.")
                        .build();
            }

            // Detect orphaned reservations (reserved > quantity — DB constraint prevents it,
            // but double-check for corruption detection)
            long orphanedCount = inventoryRepository.countViolatingReservedConstraint();
            if (orphanedCount > 0) {
                log.error("INTEGRITY VIOLATION: {} inventory records have reserved > quantity",
                        orphanedCount);
                return Health.down()
                        .withDetail("inventory_records", count)
                        .withDetail("constraint_violations", orphanedCount)
                        .withDetail("reason", "Inventory records violate reserved <= quantity constraint")
                        .build();
            }

            return Health.up()
                    .withDetail("inventory_records", count)
                    .build();

        } catch (Exception e) {
            log.warn("Inventory integrity check failed: {}", e.getMessage());
            return Health.status(CatalogHealthStatus.DEGRADED)
                    .withDetail("reason", "Could not query inventory: " + e.getMessage())
                    .build();
        }
    }
}
