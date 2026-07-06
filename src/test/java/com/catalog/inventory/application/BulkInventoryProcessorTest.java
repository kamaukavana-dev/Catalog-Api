package com.catalog.inventory.application;

import com.catalog.common.observability.metrics.InventoryMetrics;
import com.catalog.inventory.domain.Inventory;
import com.catalog.inventory.infrastructure.InventoryJournalRepository;
import com.catalog.inventory.infrastructure.InventoryRepository;
import com.catalog.variant.domain.Variant;
import com.catalog.variant.infrastructure.VariantRepository;
import com.catalog.warehouse.domain.Warehouse;
import com.catalog.warehouse.infrastructure.WarehouseRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BulkInventoryProcessorTest {

    private final VariantRepository variantRepository = mock(VariantRepository.class);
    private final WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);
    private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);
    private final InventoryJournalRepository journalRepository = mock(InventoryJournalRepository.class);
    private final InventoryMetrics inventoryMetrics = mock(InventoryMetrics.class);

    private final BulkInventoryProcessor processor = new BulkInventoryProcessor(
            inventoryRepository, journalRepository, variantRepository, warehouseRepository, inventoryMetrics);

    private Inventory stubInventory(int quantity) {
        Variant variant = mock(Variant.class);
        when(variant.getId()).thenReturn(UUID.randomUUID());
        Warehouse warehouse = mock(Warehouse.class);
        when(warehouse.getId()).thenReturn(UUID.randomUUID());

        Inventory inventory = Inventory.create(variant, warehouse, 10);
        inventory.receiveStock(quantity);

        when(variantRepository.findActiveByInternalSku("SKU-1")).thenReturn(Optional.of(variant));
        when(warehouseRepository.findActiveByCode("WH-1")).thenReturn(Optional.of(warehouse));
        when(inventoryRepository.findActiveByVariantAndWarehouse(any(), any())).thenReturn(Optional.of(inventory));
        return inventory;
    }

    @Test
    void acceptsReconciliationSpellingForAdjustmentType() {
        stubInventory(100);

        var row = new BulkInventoryService.AdjustmentRow("SKU-1", "WH-1", "RECONCILIATION", 40, "stocktake");
        BulkInventoryService.BatchResult result = processor.processBatch(List.of(row), 0);

        // Before the fix, "RECONCILIATION" fell through to the default branch and was
        // rejected as an unknown type — even though the error message advertised it.
        assertThat(result.errors()).isEmpty();
        assertThat(result.processedCount()).isEqualTo(1);
    }

    @Test
    void acceptsShortReconcileSpellingToo() {
        stubInventory(100);

        var row = new BulkInventoryService.AdjustmentRow("SKU-1", "WH-1", "reconcile", 40, "stocktake");
        BulkInventoryService.BatchResult result = processor.processBatch(List.of(row), 0);

        assertThat(result.errors()).isEmpty();
        assertThat(result.processedCount()).isEqualTo(1);
    }

    @Test
    void rejectsUnknownTypeWithAccurateMessage() {
        stubInventory(100);

        var row = new BulkInventoryService.AdjustmentRow("SKU-1", "WH-1", "SUBTRACT", 40, "bad");
        BulkInventoryService.BatchResult result = processor.processBatch(List.of(row), 0);

        assertThat(result.processedCount()).isZero();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).error()).contains("RECEIVE, RECONCILE");
    }
}
