package com.catalog.inventory.application;

import com.catalog.common.BaseIntegrationTest;
import com.catalog.common.exception.InsufficientStockException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.inventory.api.dto.request.TransferStockRequest;
import com.catalog.inventory.api.dto.response.TransferResponse;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import java.time.Duration;

public class InventoryTransferServiceIT extends BaseIntegrationTest {

    @Autowired
    private InventoryTransferService transferService;

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
    private Warehouse warehouseA;
    private Warehouse warehouseB;
    private Inventory inventoryA;
    private Inventory inventoryB;

    @BeforeEach
    void setUp() {
        journalRepository.deleteAll();
        inventoryRepository.deleteAll();
        variantRepository.deleteAll();
        productRepository.deleteAll();
        warehouseRepository.deleteAll();

        Product product = Product.createDraft("Test Product", "test-product-" + UUID.randomUUID());
        product = productRepository.save(product);

        testVariant = Variant.createDraft(product, "SKU-TRANSFER-" + UUID.randomUUID(), new BigDecimal("100.00"), TaxClass.STANDARD);
        testVariant = variantRepository.save(testVariant);

        warehouseA = Warehouse.create("WHA", "Warehouse A", WarehouseType.MAIN);
        warehouseA = warehouseRepository.save(warehouseA);

        warehouseB = Warehouse.create("WHB", "Warehouse B", WarehouseType.MAIN);
        warehouseB = warehouseRepository.save(warehouseB);

        inventoryA = Inventory.create(testVariant, warehouseA, 10);
        inventoryA.receiveStock(100);
        inventoryA = inventoryRepository.save(inventoryA);

        inventoryB = Inventory.create(testVariant, warehouseB, 10);
        inventoryB.receiveStock(50);
        inventoryB = inventoryRepository.save(inventoryB);
    }

    @Test
    void shouldTransferSuccessfully_whenStockIsAvailable() {
        TransferStockRequest request = new TransferStockRequest(inventoryA.getId(), inventoryB.getId(), 20, "Moving stock");
        
        TransferResponse response = transferService.transfer(request);

        assertThat(response.transferReferenceId()).isNotNull();
        assertThat(response.sourceAvailableQuantityAfter()).isEqualTo(80);
        assertThat(response.destinationAvailableQuantityAfter()).isEqualTo(70);

        Inventory updatedA = inventoryRepository.findById(inventoryA.getId()).orElseThrow();
        Inventory updatedB = inventoryRepository.findById(inventoryB.getId()).orElseThrow();

        assertThat(updatedA.getQuantity()).isEqualTo(80);
        assertThat(updatedB.getQuantity()).isEqualTo(70);

        List<InventoryJournal> journals = journalRepository.findAll();
        assertThat(journals).hasSize(2);
        assertThat(journals).allMatch(j -> j.getReferenceId().equals(response.transferReferenceId()));
        assertThat(journals).extracting(InventoryJournal::getOperationType)
                .containsExactlyInAnyOrder(InventoryOperationType.TRANSFER_OUT, InventoryOperationType.TRANSFER_IN);
    }

    @Test
    void shouldThrowInsufficientStock_whenSourceStockIsInsufficient() {
        TransferStockRequest request = new TransferStockRequest(inventoryA.getId(), inventoryB.getId(), 150, "Too much");

        assertThrows(InsufficientStockException.class, () -> transferService.transfer(request));

        Inventory updatedA = inventoryRepository.findById(inventoryA.getId()).orElseThrow();
        Inventory updatedB = inventoryRepository.findById(inventoryB.getId()).orElseThrow();

        assertThat(updatedA.getQuantity()).isEqualTo(100);
        assertThat(updatedB.getQuantity()).isEqualTo(50);
        assertThat(journalRepository.count()).isZero();
    }

    @Test
    void shouldHandleConcurrentTransfers_withoutNegativeStock() throws InterruptedException {
        int initialQty = 100;
        int transferAmount = 1;
        int numThreads = 110; 

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        ExecutorService executor = Executors.newFixedThreadPool(20);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    transferService.transfer(new TransferStockRequest(inventoryA.getId(), inventoryB.getId(), transferAmount, "Concurrent"));
                    successCount.incrementAndGet();
                } catch (InsufficientStockException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        Inventory finalA = inventoryRepository.findById(inventoryA.getId()).orElseThrow();
        Inventory finalB = inventoryRepository.findById(inventoryB.getId()).orElseThrow();

        assertThat(finalA.getQuantity()).isGreaterThanOrEqualTo(0);
        assertThat(successCount.get()).isEqualTo(initialQty);
        assertThat(failureCount.get()).isEqualTo(numThreads - initialQty);
        assertThat(finalB.getQuantity()).isEqualTo(50 + initialQty);
    }

    @Test
    void shouldPreventDeadlock_whenCircularTransfersOccur() throws InterruptedException {
        assertTimeout(Duration.ofSeconds(10), () -> {
            int numThreads = 20;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numThreads);
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);

            for (int i = 0; i < numThreads; i++) {
                final boolean toggle = i % 2 == 0;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (toggle) {
                            transferService.transfer(new TransferStockRequest(inventoryA.getId(), inventoryB.getId(), 1, "A to B"));
                        } else {
                            transferService.transfer(new TransferStockRequest(inventoryB.getId(), inventoryA.getId(), 1, "B to A"));
                        }
                    } catch (Exception e) {
                        // Success/failure doesn't matter, only that it doesn't deadlock
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();
        });
    }

    @Test
    void shouldThrowNotFound_whenDestinationDoesNotExist() {
        UUID fakeId = UUID.randomUUID();
        TransferStockRequest request = new TransferStockRequest(inventoryA.getId(), fakeId, 10, "Fake dest");

        assertThrows(ResourceNotFoundException.class, () -> transferService.transfer(request));
    }
}
