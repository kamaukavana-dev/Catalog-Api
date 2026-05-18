package com.catalog.inventory.application;

import com.catalog.brand.domain.Brand;
import com.catalog.brand.infrastructure.BrandRepository;
import com.catalog.category.domain.Category;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.common.BaseIntegrationTest;
import com.catalog.common.exception.InsufficientStockException;
import com.catalog.inventory.api.dto.request.CreateReservationRequest;
import com.catalog.inventory.domain.Inventory;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryConcurrencyIT extends BaseIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private VariantRepository variantRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void concurrentReservationsDoNotOversell() throws Exception {
        UUID variantId = seedVariant();
        UUID warehouseId = seedWarehouse();

        Inventory inventory = Inventory.create(
                variantRepository.findById(variantId).orElseThrow(),
                warehouseRepository.findById(warehouseId).orElseThrow(),
                0
        );
        inventory.receiveStock(1);
        inventoryRepository.save(inventory);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        Runnable reserveTask = () -> {
            try {
                start.await(5, TimeUnit.SECONDS);
                inventoryService.reserveStock(new CreateReservationRequest(
                        variantId,
                        warehouseId,
                        UUID.randomUUID(),
                        1
                ));
                successCount.incrementAndGet();
            } catch (InsufficientStockException ex) {
                failureCount.incrementAndGet();
            } catch (Exception ex) {
                failureCount.incrementAndGet();
            }
        };

        executor.submit(reserveTask);
        executor.submit(reserveTask);
        start.countDown();

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        Inventory refreshed = inventoryRepository.findById(inventory.getId()).orElseThrow();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(refreshed.getReservedQuantity()).isEqualTo(1);
        assertThat(refreshed.getAvailableQuantity()).isEqualTo(0);
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

    private UUID seedWarehouse() {
        Warehouse warehouse = Warehouse.create("WH-1", "Main Warehouse", WarehouseType.MAIN);
        return warehouseRepository.save(warehouse).getId();
    }
}
