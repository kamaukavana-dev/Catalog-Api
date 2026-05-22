package com.catalog.inventory.application;

import com.catalog.brand.domain.Brand;
import com.catalog.brand.infrastructure.BrandRepository;
import com.catalog.category.domain.Category;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.common.BaseIntegrationTest;
import com.catalog.common.exception.InsufficientStockException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.inventory.api.dto.request.TransferStockRequest;
import com.catalog.inventory.api.dto.response.TransferResponse;
import com.catalog.inventory.domain.Inventory;
import com.catalog.inventory.domain.InventoryJournal;
import com.catalog.inventory.domain.InventoryOperationType;
import com.catalog.inventory.infrastructure.InventoryJournalRepository;
import com.catalog.inventory.infrastructure.InventoryRepository;
import com.catalog.product.domain.Product;
import com.catalog.product.domain.ProductStatus;
import com.catalog.product.infrastructure.ProductRepository;
import com.catalog.variant.domain.TaxClass;
import com.catalog.variant.domain.Variant;
import com.catalog.variant.domain.VariantStatus;
import com.catalog.variant.infrastructure.VariantRepository;
import com.catalog.warehouse.domain.Warehouse;
import com.catalog.warehouse.domain.WarehouseType;
import com.catalog.warehouse.infrastructure.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class InventoryTransferServiceIT extends BaseIntegrationTest {

    @Autowired private InventoryTransferService transferService;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private InventoryJournalRepository journalRepository;

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private VariantRepository variantRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        journalRepository.deleteAll();
        inventoryRepository.deleteAll();
        variantRepository.deleteAll();
        productRepository.deleteAll();
        brandRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
    }

    @Test
    void shouldTransferStockAtomically_whenTransferSucceeds() {
        UUID variantId = seedVariant();
        Warehouse whA = warehouseRepository.save(Warehouse.create("WH-A", "Warehouse A", WarehouseType.MAIN));
        Warehouse whB = warehouseRepository.save(Warehouse.create("WH-B", "Warehouse B", WarehouseType.MAIN));

        Inventory source = Inventory.create(
                variantRepository.findById(variantId).orElseThrow(),
                whA,
                0
        );
        source.receiveStock(50);
        source = inventoryRepository.save(source);

        Inventory dest = Inventory.create(
                variantRepository.findById(variantId).orElseThrow(),
                whB,
                0
        );
        dest = inventoryRepository.save(dest);

        TransferResponse response = transferService.transfer(new TransferStockRequest(
                source.getId(),
                dest.getId(),
                10,
                "move stock"
        ));

        Inventory refreshedSource = inventoryRepository.findById(source.getId()).orElseThrow();
        Inventory refreshedDest = inventoryRepository.findById(dest.getId()).orElseThrow();

        assertThat(refreshedSource.getQuantity()).isEqualTo(40);
        assertThat(refreshedDest.getQuantity()).isEqualTo(10);

        List<InventoryJournal> journal = journalRepository.findTransferJournalByReferenceId(response.transferReferenceId());
        assertThat(journal).hasSize(2);
        assertThat(journal.get(0).getOperationType()).isEqualTo(InventoryOperationType.TRANSFER_OUT);
        assertThat(journal.get(1).getOperationType()).isEqualTo(InventoryOperationType.TRANSFER_IN);
    }

    @Test
    void shouldNotChangeInventoryOrJournal_whenTransferFailsForInsufficientStock() {
        UUID variantId = seedVariant();
        Warehouse whA = warehouseRepository.save(Warehouse.create("WH-A", "Warehouse A", WarehouseType.MAIN));
        Warehouse whB = warehouseRepository.save(Warehouse.create("WH-B", "Warehouse B", WarehouseType.MAIN));

        Inventory source = Inventory.create(
                variantRepository.findById(variantId).orElseThrow(),
                whA,
                0
        );
        source.receiveStock(5);
        source = inventoryRepository.save(source);

        Inventory dest = inventoryRepository.save(Inventory.create(
                variantRepository.findById(variantId).orElseThrow(),
                whB,
                0
        ));

        UUID sourceId = source.getId();
        UUID destId = dest.getId();

        assertThatThrownBy(() -> transferService.transfer(new TransferStockRequest(
                sourceId,
                destId,
                10,
                "too much"
        ))).isInstanceOf(InsufficientStockException.class);

        Inventory refreshedSource = inventoryRepository.findById(sourceId).orElseThrow();
        Inventory refreshedDest = inventoryRepository.findById(destId).orElseThrow();

        assertThat(refreshedSource.getQuantity()).isEqualTo(5);
        assertThat(refreshedDest.getQuantity()).isEqualTo(0);
        assertThat(journalRepository.findAll()).isEmpty();
    }

    @Test
    void shouldPreventNegativeQuantity_whenConcurrentTransfersFromSingleSource() throws Exception {
        UUID variantId = seedVariant();
        Warehouse whA = warehouseRepository.save(Warehouse.create("WH-A", "Warehouse A", WarehouseType.MAIN));
        Warehouse whB = warehouseRepository.save(Warehouse.create("WH-B", "Warehouse B", WarehouseType.MAIN));

        Inventory source = Inventory.create(variantRepository.findById(variantId).orElseThrow(), whA, 0);
        source.receiveStock(50);
        source = inventoryRepository.save(source);

        Inventory dest = inventoryRepository.save(Inventory.create(variantRepository.findById(variantId).orElseThrow(), whB, 0));

        int transferQty = 7;
        int threads = 10;
        UUID sourceId = source.getId();
        UUID destId = dest.getId();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    transferService.transfer(new TransferStockRequest(
                            sourceId,
                            destId,
                            transferQty,
                            "concurrent"
                    ));
                    successCount.incrementAndGet();
                } catch (InsufficientStockException ignored) {
                    // expected for some threads
                } catch (Exception ignored) {
                    // count as failure; assertions below validate invariants
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        Inventory refreshedSource = inventoryRepository.findById(sourceId).orElseThrow();
        Inventory refreshedDest = inventoryRepository.findById(destId).orElseThrow();

        int expectedTransferred = successCount.get() * transferQty;
        assertThat(expectedTransferred).isLessThanOrEqualTo(50);

        assertThat(refreshedSource.getQuantity()).isGreaterThanOrEqualTo(0);
        assertThat(refreshedSource.getQuantity()).isEqualTo(50 - expectedTransferred);
        assertThat(refreshedDest.getQuantity()).isEqualTo(expectedTransferred);

        assertThat(journalRepository.findAll()).hasSize(successCount.get() * 2);
    }

    @Test
    void shouldNotDeadlock_whenTwoWayTransfersRunConcurrently() {
        UUID variantId = seedVariant();
        Warehouse whA = warehouseRepository.save(Warehouse.create("WH-A", "Warehouse A", WarehouseType.MAIN));
        Warehouse whB = warehouseRepository.save(Warehouse.create("WH-B", "Warehouse B", WarehouseType.MAIN));

        Inventory invA = Inventory.create(variantRepository.findById(variantId).orElseThrow(), whA, 0);
        invA.receiveStock(100);
        invA = inventoryRepository.save(invA);

        Inventory invB = Inventory.create(variantRepository.findById(variantId).orElseThrow(), whB, 0);
        invB.receiveStock(100);
        invB = inventoryRepository.save(invB);

        Inventory finalInvA = invA;
        Inventory finalInvB = invB;

        assertTimeout(Duration.ofSeconds(5), () -> {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);
            List<Throwable> errors = new ArrayList<>();
            AtomicInteger successes = new AtomicInteger();

            executor.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    transferService.transfer(new TransferStockRequest(finalInvA.getId(), finalInvB.getId(), 10, "A->B"));
                    successes.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    transferService.transfer(new TransferStockRequest(finalInvB.getId(), finalInvA.getId(), 15, "B->A"));
                    successes.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });

            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            assertThat(errors).isEmpty();
            assertThat(successes.get()).isEqualTo(2);
        });

        Inventory refreshedA = inventoryRepository.findById(invA.getId()).orElseThrow();
        Inventory refreshedB = inventoryRepository.findById(invB.getId()).orElseThrow();

        assertThat(refreshedA.getQuantity()).isEqualTo(105);
        assertThat(refreshedB.getQuantity()).isEqualTo(95);
        assertThat(journalRepository.findAll()).hasSize(4);
    }

    @Test
    void shouldThrowResourceNotFoundException_whenInventoryDoesNotExist() {
        UUID variantId = seedVariant();
        Warehouse whA = warehouseRepository.save(Warehouse.create("WH-A", "Warehouse A", WarehouseType.MAIN));

        Inventory source = Inventory.create(variantRepository.findById(variantId).orElseThrow(), whA, 0);
        source.receiveStock(10);
        source = inventoryRepository.save(source);
        UUID sourceId = source.getId();

        assertThatThrownBy(() -> transferService.transfer(new TransferStockRequest(
                sourceId,
                UUID.randomUUID(),
                1,
                "missing"
        ))).isInstanceOf(ResourceNotFoundException.class);
    }

    private UUID seedVariant() {
        Brand brand = brandRepository.save(Brand.create("Test Brand", "test-brand", "desc"));

        Category category = categoryRepository.save(Category.createRoot("Root", "root", "desc"));
        category.initializePath();
        categoryRepository.save(category);

        Product product = Product.createDraft("Test Product", "test-product");
        product.assignBrand(brand);
        product.assignPrimaryCategory(category);
        product.transitionTo(ProductStatus.ACTIVE);
        Product savedProduct = productRepository.save(product);

        Variant variant = Variant.createDraft(savedProduct, "SKU-1", BigDecimal.valueOf(10), TaxClass.STANDARD);
        variant.setStatus(VariantStatus.ACTIVE);
        return variantRepository.save(variant).getId();
    }
}
