package com.catalog.inventory.application;

import com.catalog.inventory.domain.ActorType;
import com.catalog.inventory.domain.Inventory;
import com.catalog.inventory.domain.InventoryJournal;
import com.catalog.inventory.domain.InventoryOperationType;
import com.catalog.inventory.infrastructure.InventoryJournalRepository;
import com.catalog.inventory.infrastructure.InventoryRepository;
import com.catalog.variant.infrastructure.VariantRepository;
import com.catalog.warehouse.infrastructure.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BulkInventoryProcessor {

    private final InventoryRepository inventoryRepository;
    private final InventoryJournalRepository journalRepository;
    private final VariantRepository variantRepository;
    private final WarehouseRepository warehouseRepository;
    private final com.catalog.common.observability.metrics.InventoryMetrics inventoryMetrics;

    /**
     * Processes one batch in its own transaction.
     * Propagation.REQUIRES_NEW ensures this batch commits or rolls back independently.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BulkInventoryService.BatchResult processBatch(List<BulkInventoryService.AdjustmentRow> batch, int rowOffset) {
        int processed = 0;
        List<BulkInventoryService.RowError> errors = new ArrayList<>();

        for (int i = 0; i < batch.size(); i++) {
            BulkInventoryService.AdjustmentRow row = batch.get(i);
            int absoluteRow = rowOffset + i + 2;

            try {
                processRow(row);
                processed++;
                inventoryMetrics.recordBulkRow(true);
            } catch (Exception e) {
                errors.add(new BulkInventoryService.RowError(absoluteRow, row.variantSku(),
                        row.warehouseCode(), e.getMessage()));
                inventoryMetrics.recordBulkRow(false);
                log.warn("Bulk import row {} failed: sku={} warehouse={}: {}",
                        absoluteRow, row.variantSku(), row.warehouseCode(), e.getMessage());

                // Defensive behavior: once a row fails, stop applying further changes.
                // Mark remaining rows in this batch as failed without processing them.
                for (int j = i + 1; j < batch.size(); j++) {
                    BulkInventoryService.AdjustmentRow skipped = batch.get(j);
                    int skippedRow = rowOffset + j + 2;
                    errors.add(new BulkInventoryService.RowError(
                            skippedRow,
                            skipped.variantSku(),
                            skipped.warehouseCode(),
                            "Skipped due to previous row failure"
                    ));
                    inventoryMetrics.recordBulkRow(false);
                }
                break;
            }
        }

        return new BulkInventoryService.BatchResult(processed, errors);
    }

    private void processRow(BulkInventoryService.AdjustmentRow row) {
        var variant = variantRepository.findActiveByInternalSku(row.variantSku())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Variant not found: " + row.variantSku()));

        var warehouse = warehouseRepository.findActiveByCode(row.warehouseCode())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Warehouse not found: " + row.warehouseCode()));

        Inventory inventory = inventoryRepository
                .findActiveByVariantAndWarehouse(variant.getId(), warehouse.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "No inventory record for variant " + row.variantSku() +
                    " at warehouse " + row.warehouseCode() +
                    ". Create inventory record first."));

        int quantityBefore = inventory.getQuantity();
        int reservedBefore = inventory.getReservedQuantity();

        InventoryOperationType opType;
        switch (row.adjustmentType().toUpperCase()) {
            case "RECEIVE" -> {
                inventory.receiveStock(row.quantity());
                opType = InventoryOperationType.RECEIVE;
            }
            case "RECONCILE" -> {
                inventory.reconcileQuantity(row.quantity());
                opType = InventoryOperationType.RECONCILIATION;
            }
            default -> throw new IllegalArgumentException(
                "Unknown adjustment type: " + row.adjustmentType() +
                ". Allowed: RECEIVE, RECONCILE");
        }

        inventoryRepository.save(inventory);

        journalRepository.save(InventoryJournal.forQuantityChange(
            inventory, opType,
            quantityBefore, reservedBefore,
            "BULK_IMPORT", null,
            ActorType.ERP_INTEGRATION, null, row.reason()
        ));
    }
}
