package com.catalog.inventory.application;

import com.catalog.brand.domain.Brand;
import com.catalog.brand.infrastructure.BrandRepository;
import com.catalog.category.domain.Category;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.common.BaseIntegrationTest;
import com.catalog.inventory.api.dto.request.CreateInventoryRequest;
import com.catalog.inventory.domain.BulkImportJob;
import com.catalog.inventory.infrastructure.BulkImportJobRepository;
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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BulkInventoryServiceIT extends BaseIntegrationTest {

    @Autowired private BulkInventoryService bulkInventoryService;
    @Autowired private InventoryService inventoryService;

    @Autowired private BulkImportJobRepository jobRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private InventoryJournalRepository journalRepository;

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private VariantRepository variantRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        journalRepository.deleteAll();
        inventoryRepository.deleteAll();
        variantRepository.deleteAll();
        productRepository.deleteAll();
        brandRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
    }

    @Test
    void shouldCompleteJobAndPersistAllRows_whenValid150RowImportSubmitted() {
        UUID variantId = seedVariant("SKU-BULK-OK");
        Warehouse wh = warehouseRepository.save(Warehouse.create("WH-BULK", "Bulk Warehouse", WarehouseType.MAIN));
        inventoryService.createInventory(new CreateInventoryRequest(variantId, wh.getId(), 0, 0));

        UUID sessionId = UUID.randomUUID();
        MockMultipartFile file = csvFile(buildCsv(150, "SKU-BULK-OK", "WH-BULK", false));

        BulkImportJob submitted = bulkInventoryService.submitImport(sessionId, file);

        BulkImportJob completed = Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> bulkInventoryService.getJobStatus(submitted.getId()),
                        j -> "COMPLETED".equals(j.getStatus()));

        assertThat(completed.getTotalRows()).isEqualTo(150);
        assertThat(completed.getProcessedRows()).isEqualTo(150);
        assertThat(completed.getFailedRows()).isEqualTo(0);
        assertThat(completed.getErrorSummary()).isNull();

        var inv = inventoryRepository.findActiveByVariantAndWarehouse(variantId, wh.getId()).orElseThrow();
        assertThat(inv.getQuantity()).isEqualTo(150);
        assertThat(journalRepository.findAll()).hasSize(150);
    }

    @Test
    void shouldFailFastAfterFirstInvalidRow_whenRow76HasNegativeQuantity() {
        UUID variantId = seedVariant("SKU-BULK-BAD");
        Warehouse wh = warehouseRepository.save(Warehouse.create("WH-BULK", "Bulk Warehouse", WarehouseType.MAIN));
        inventoryService.createInventory(new CreateInventoryRequest(variantId, wh.getId(), 0, 0));

        UUID sessionId = UUID.randomUUID();
        MockMultipartFile file = csvFile(buildCsv(150, "SKU-BULK-BAD", "WH-BULK", true));

        BulkImportJob submitted = bulkInventoryService.submitImport(sessionId, file);

        BulkImportJob finished = Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> bulkInventoryService.getJobStatus(submitted.getId()),
                        j -> "PARTIALLY_FAILED".equals(j.getStatus()));

        assertThat(finished.getTotalRows()).isEqualTo(150);
        assertThat(finished.getProcessedRows()).isEqualTo(75);
        assertThat(finished.getFailedRows()).isEqualTo(75);
        assertThat(finished.getErrorSummary()).isNotBlank();

        var inv = inventoryRepository.findActiveByVariantAndWarehouse(variantId, wh.getId()).orElseThrow();
        assertThat(inv.getQuantity()).isEqualTo(75);
        assertThat(journalRepository.findAll()).hasSize(75);
    }

    @Test
    void shouldReturnExistingJob_whenSameImportSessionIdSubmittedTwice() {
        UUID variantId = seedVariant("SKU-BULK-IDEMP");
        Warehouse wh = warehouseRepository.save(Warehouse.create("WH-BULK", "Bulk Warehouse", WarehouseType.MAIN));
        inventoryService.createInventory(new CreateInventoryRequest(variantId, wh.getId(), 0, 0));

        UUID sessionId = UUID.randomUUID();
        MockMultipartFile file = csvFile(buildCsv(10, "SKU-BULK-IDEMP", "WH-BULK", false));

        BulkImportJob first = bulkInventoryService.submitImport(sessionId, file);
        BulkImportJob second = bulkInventoryService.submitImport(sessionId, file);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(jobRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldTransitionPendingToInProgressToCompleted_whenValidImportRuns() {
        UUID variantId = seedVariant("SKU-BULK-STATE");
        Warehouse wh = warehouseRepository.save(Warehouse.create("WH-BULK", "Bulk Warehouse", WarehouseType.MAIN));
        inventoryService.createInventory(new CreateInventoryRequest(variantId, wh.getId(), 0, 0));

        UUID sessionId = UUID.randomUUID();
        MockMultipartFile file = csvFile(buildCsv(150, "SKU-BULK-STATE", "WH-BULK", false));

        BulkImportJob submitted = bulkInventoryService.submitImport(sessionId, file);
        assertThat(submitted.getStatus()).isEqualTo("PENDING");

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollDelay(Duration.ZERO)
                .pollInterval(Duration.ofMillis(10))
                .until(() -> bulkInventoryService.getJobStatus(submitted.getId()).getStatus(),
                        s -> "IN_PROGRESS".equals(s));

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> bulkInventoryService.getJobStatus(submitted.getId()).getStatus(),
                        s -> "COMPLETED".equals(s));
    }

    private MockMultipartFile csvFile(String csv) {
        return new MockMultipartFile(
                "file",
                "bulk.csv",
                "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private String buildCsv(int rows, String sku, String whCode, boolean row76Negative) {
        StringBuilder sb = new StringBuilder();
        sb.append("variant_sku,warehouse_code,adjustment_type,quantity,reason\n");
        for (int i = 1; i <= rows; i++) {
            int qty = 1;
            if (row76Negative && i == 76) {
                qty = -1;
            }
            sb.append(sku).append(',')
                    .append(whCode).append(',')
                    .append("RECEIVE").append(',')
                    .append(qty).append(',')
                    .append("row-").append(i)
                    .append('\n');
        }
        return sb.toString();
    }

    private UUID seedVariant(String internalSku) {
        Brand brand = brandRepository.save(Brand.create("Test Brand", "test-brand", "desc"));

        Category category = categoryRepository.save(Category.createRoot("Root", "root", "desc"));
        category.initializePath();
        categoryRepository.save(category);

        Product product = Product.createDraft("Test Product", "test-product");
        product.assignBrand(brand);
        product.assignPrimaryCategory(category);
        product.transitionTo(ProductStatus.ACTIVE);
        Product savedProduct = productRepository.save(product);

        Variant variant = Variant.createDraft(savedProduct, internalSku, BigDecimal.valueOf(10), TaxClass.STANDARD);
        variant.setStatus(VariantStatus.ACTIVE);
        return variantRepository.save(variant).getId();
    }
}
