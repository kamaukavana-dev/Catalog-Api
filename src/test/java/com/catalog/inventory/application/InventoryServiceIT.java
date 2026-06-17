package com.catalog.inventory.application;

import com.catalog.common.BaseIntegrationTest;
import com.catalog.common.exception.InsufficientStockException;
import com.catalog.inventory.api.dto.request.AdjustStockRequest;
import com.catalog.inventory.api.dto.request.CreateInventoryRequest;
import com.catalog.inventory.api.dto.response.InventoryResponse;
import com.catalog.inventory.domain.*;
import com.catalog.inventory.infrastructure.InventoryJournalRepository;
import com.catalog.inventory.infrastructure.InventoryRepository;
import com.catalog.product.domain.Product;
import com.catalog.product.infrastructure.ProductRepository;
import com.catalog.variant.domain.Variant;
import com.catalog.variant.domain.TaxClass;
import com.catalog.variant.infrastructure.VariantRepository;
import com.catalog.warehouse.domain.Warehouse;
import com.catalog.warehouse.domain.WarehouseType;
import com.catalog.warehouse.infrastructure.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InventoryServiceIT extends BaseIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryJournalRepository journalRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private VariantRepository variantRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    private Variant testVariant;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        journalRepository.deleteAll();
        inventoryRepository.deleteAll();
        variantRepository.deleteAll();
        productRepository.deleteAll();
        warehouseRepository.deleteAll();

        Product product = Product.createDraft("Test Product", "test-product-" + UUID.randomUUID());
        product = productRepository.save(product);

        testVariant = Variant.createDraft(product, "SKU-INV-" + UUID.randomUUID(), new BigDecimal("100.00"), TaxClass.STANDARD);
        testVariant = variantRepository.save(testVariant);

        warehouse = Warehouse.create("WH1", "Warehouse 1", WarehouseType.MAIN);
        warehouse = warehouseRepository.save(warehouse);
    }

    @Test
    void shouldAdjustStockUpAndCreateJournal() {
        InventoryResponse inv = inventoryService.createInventory(new CreateInventoryRequest(testVariant.getId(), warehouse.getId(), 10, 10));
        
        inventoryService.adjustStock(inv.id(), new AdjustStockRequest(AdjustStockRequest.AdjustmentType.RECEIVE, 50, "Incoming"));

        Inventory updated = inventoryRepository.findById(inv.id()).orElseThrow();
        assertThat(updated.getQuantity()).isEqualTo(60);

        List<InventoryJournal> journals = journalRepository.findAll();
        // createInventory also creates a journal entry (RECEIVE)
        assertThat(journals).hasSize(2);
    }

    @Test
    void shouldAdjustStockDownToZero() {
        InventoryResponse inv = inventoryService.createInventory(new CreateInventoryRequest(testVariant.getId(), warehouse.getId(), 10, 50));
        
        inventoryService.adjustStock(inv.id(), new AdjustStockRequest(AdjustStockRequest.AdjustmentType.RECONCILE, 0, "Stocktake zero"));

        Inventory updated = inventoryRepository.findById(inv.id()).orElseThrow();
        assertThat(updated.getQuantity()).isZero();
    }

    @Test
    void shouldThrowInsufficientStock_whenAdjustingBelowZero() {
        InventoryResponse inv = inventoryService.createInventory(new CreateInventoryRequest(testVariant.getId(), warehouse.getId(), 10, 10));
        
        // RECONCILE to -5 should fail
        assertThrows(RuntimeException.class, () -> 
            inventoryService.adjustStock(inv.id(), new AdjustStockRequest(AdjustStockRequest.AdjustmentType.RECONCILE, -5, "Bad")));
    }

    @Test
    void shouldHandleConcurrentAdjustments_withoutLostUpdates() throws InterruptedException {
        InventoryResponse inv = inventoryService.createInventory(new CreateInventoryRequest(testVariant.getId(), warehouse.getId(), 10, 10));
        
        int numThreads = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    inventoryService.adjustStock(inv.id(), new AdjustStockRequest(AdjustStockRequest.AdjustmentType.RECEIVE, 1, "Concurrent"));
                } catch (Exception e) {
                    // unexpected but optimistic lock retry should handle it
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        Inventory finalInv = inventoryRepository.findById(inv.id()).orElseThrow();
        assertThat(finalInv.getQuantity()).isEqualTo(10 + numThreads);
    }
}
