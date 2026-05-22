package com.catalog.inventory.application;

import com.catalog.brand.domain.Brand;
import com.catalog.brand.infrastructure.BrandRepository;
import com.catalog.category.domain.Category;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.common.BaseIntegrationTest;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.inventory.api.dto.request.AdjustStockRequest;
import com.catalog.inventory.api.dto.request.CreateInventoryRequest;
import com.catalog.inventory.api.dto.response.InventoryResponse;
import com.catalog.inventory.domain.ActorType;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryServiceIT extends BaseIntegrationTest {

    @Autowired private InventoryService inventoryService;
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
    void shouldCreateJournalEntryWithCorrectDeltaAndActor_whenAdjustStockUp() {
        UUID variantId = seedVariant();
        UUID warehouseId = seedWarehouse();

        InventoryResponse created = inventoryService.createInventory(new CreateInventoryRequest(
                variantId,
                warehouseId,
                0,
                0
        ));

        InventoryResponse adjusted = inventoryService.adjustStock(created.id(), new AdjustStockRequest(
                AdjustStockRequest.AdjustmentType.RECEIVE,
                5,
                "receive"
        ));

        assertThat(adjusted.quantity()).isEqualTo(5);
        assertThat(adjusted.reservedQuantity()).isEqualTo(0);

        List<InventoryJournal> journal = journalRepository.findAll();
        assertThat(journal).hasSize(1);
        InventoryJournal j = journal.get(0);
        assertThat(j.getInventoryId()).isEqualTo(created.id());
        assertThat(j.getOperationType()).isEqualTo(InventoryOperationType.RECEIVE);
        assertThat(j.getQuantityBefore()).isEqualTo(0);
        assertThat(j.getQuantityAfter()).isEqualTo(5);
        assertThat(j.getQuantityDelta()).isEqualTo(5);
        assertThat(j.getReservedBefore()).isEqualTo(0);
        assertThat(j.getReservedAfter()).isEqualTo(0);
        assertThat(j.getReservedDelta()).isEqualTo(0);
        assertThat(j.getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(j.getActorId()).isNull();
    }

    @Test
    void shouldAllowReconcileDownToZero_whenAdjustStockDownToZero() {
        UUID variantId = seedVariant();
        UUID warehouseId = seedWarehouse();

        InventoryResponse created = inventoryService.createInventory(new CreateInventoryRequest(
                variantId,
                warehouseId,
                10,
                0
        ));

        InventoryResponse reconciled = inventoryService.adjustStock(created.id(), new AdjustStockRequest(
                AdjustStockRequest.AdjustmentType.RECONCILE,
                0,
                "stocktake"
        ));

        assertThat(reconciled.quantity()).isEqualTo(0);
        assertThat(reconciled.availableQuantity()).isEqualTo(0);
    }

    @Test
    void shouldRejectNegativeQuantity_whenAdjustStockDownBelowZero() {
        UUID variantId = seedVariant();
        UUID warehouseId = seedWarehouse();

        InventoryResponse created = inventoryService.createInventory(new CreateInventoryRequest(
                variantId,
                warehouseId,
                10,
                0
        ));

        assertThatThrownBy(() -> inventoryService.adjustStock(created.id(), new AdjustStockRequest(
                AdjustStockRequest.AdjustmentType.RECONCILE,
                -1,
                "invalid"
        ))).isInstanceOf(BusinessRuleViolationException.class);

        // unchanged
        assertThat(inventoryRepository.findById(created.id()).orElseThrow().getQuantity()).isEqualTo(10);
    }

    @Test
    void shouldThrowResourceNotFoundException_whenAdjustingNonExistentInventory() {
        assertThatThrownBy(() -> inventoryService.adjustStock(UUID.randomUUID(), new AdjustStockRequest(
                AdjustStockRequest.AdjustmentType.RECEIVE,
                1,
                "noop"
        ))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldNotLoseUpdates_whenTwentyConcurrentIncrementsOccur() throws Exception {
        UUID variantId = seedVariant();
        UUID warehouseId = seedWarehouse();

        InventoryResponse created = inventoryService.createInventory(new CreateInventoryRequest(
                variantId,
                warehouseId,
                0,
                0
        ));

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    inventoryService.adjustStock(created.id(), new AdjustStockRequest(
                            AdjustStockRequest.AdjustmentType.RECEIVE,
                            1,
                            "inc"
                    ));
                    success.incrementAndGet();
                } catch (Exception ignored) {
                    // retry logic is inside the service; failures will surface as missing increments
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        assertThat(success.get()).isEqualTo(threads);
        assertThat(inventoryRepository.findById(created.id()).orElseThrow().getQuantity()).isEqualTo(threads);
    }

    @Test
    void shouldNeverUpdateExistingJournalRows_whenMultipleAdjustmentsOccur() {
        UUID variantId = seedVariant();
        UUID warehouseId = seedWarehouse();

        InventoryResponse created = inventoryService.createInventory(new CreateInventoryRequest(
                variantId,
                warehouseId,
                0,
                0
        ));

        inventoryService.adjustStock(created.id(), new AdjustStockRequest(
                AdjustStockRequest.AdjustmentType.RECEIVE,
                3,
                "first"
        ));

        List<InventoryJournal> before = journalRepository.findAll();
        assertThat(before).hasSize(1);
        InventoryJournal first = before.get(0);
        UUID firstId = first.getId();
        int firstQtyAfter = first.getQuantityAfter();
        int firstQtyDelta = first.getQuantityDelta();

        inventoryService.adjustStock(created.id(), new AdjustStockRequest(
                AdjustStockRequest.AdjustmentType.RECEIVE,
                2,
                "second"
        ));

        List<InventoryJournal> after = journalRepository.findAll();
        assertThat(after).hasSize(2);

        InventoryJournal persistedFirst = after.stream()
                .filter(j -> j.getId().equals(firstId))
                .findFirst()
                .orElseThrow();

        // If the first row was updated, these immutable values could change.
        assertThat(persistedFirst.getQuantityAfter()).isEqualTo(firstQtyAfter);
        assertThat(persistedFirst.getQuantityDelta()).isEqualTo(firstQtyDelta);
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

        Variant variant = Variant.createDraft(savedProduct, "SKU-INV-1", BigDecimal.valueOf(10), TaxClass.STANDARD);
        variant.setStatus(VariantStatus.ACTIVE);
        return variantRepository.save(variant).getId();
    }

    private UUID seedWarehouse() {
        Warehouse warehouse = Warehouse.create("WH-INV-1", "Inventory WH", WarehouseType.MAIN);
        return warehouseRepository.save(warehouse).getId();
    }
}

