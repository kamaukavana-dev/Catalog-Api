package com.catalog.inventory.application;

import com.catalog.common.BaseIntegrationTest;
import com.catalog.inventory.domain.*;
import com.catalog.inventory.infrastructure.BulkImportJobRepository;
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
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class BulkInventoryServiceIT extends BaseIntegrationTest {

    @Autowired
    private BulkInventoryService bulkInventoryService;

    @Autowired
    private BulkImportJobRepository jobRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private VariantRepository variantRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductRepository productRepository;

    private String variantSku;
    private String warehouseCode = "WH-BULK";

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        inventoryRepository.deleteAll();
        variantRepository.deleteAll();
        productRepository.deleteAll();
        warehouseRepository.deleteAll();

        Product product = Product.createDraft("Bulk Product", "bulk-product-" + UUID.randomUUID());
        product = productRepository.save(product);

        variantSku = "SKU-BULK-" + UUID.randomUUID();
        Variant variant = Variant.createDraft(product, variantSku, new BigDecimal("50.00"), TaxClass.STANDARD);
        variantRepository.save(variant);

        Warehouse warehouse = Warehouse.create(warehouseCode, "Bulk Warehouse", WarehouseType.MAIN);
        warehouseRepository.save(warehouse);

        inventoryRepository.save(Inventory.create(variant, warehouse, 5));
    }

    @Test
    void shouldProcessValidImportSuccessfully() {
        UUID sessionId = UUID.randomUUID();
        String csv = "variant_sku,warehouse_code,adjustment_type,quantity,reason\n" +
                     variantSku + "," + warehouseCode + ",RECEIVE,100,Stock arrival\n";
        
        MockMultipartFile file = new MockMultipartFile("file", "import.csv", "text/csv", csv.getBytes());

        BulkImportJob job = bulkInventoryService.submitImport(sessionId, file);
        assertThat(job.getStatus()).isEqualTo("PENDING");

        await().atMost(Duration.ofSeconds(10))
                .until(() -> "COMPLETED".equals(jobRepository.findById(job.getId()).get().getStatus()));

        BulkImportJob finalJob = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(finalJob.getProcessedRows()).isEqualTo(1);
        assertThat(finalJob.getFailedRows()).isZero();

        Inventory inv = inventoryRepository.findAll().get(0);
        assertThat(inv.getQuantity()).isEqualTo(100);
    }

    @Test
    void shouldHandlePartialFailure() {
        UUID sessionId = UUID.randomUUID();
        String csv = "variant_sku,warehouse_code,adjustment_type,quantity,reason\n" +
                     variantSku + "," + warehouseCode + ",RECEIVE,50,Valid row\n" +
                     "INVALID-SKU," + warehouseCode + ",RECEIVE,10,Invalid row\n";

        MockMultipartFile file = new MockMultipartFile("file", "partial.csv", "text/csv", csv.getBytes());

        BulkImportJob job = bulkInventoryService.submitImport(sessionId, file);

        await().atMost(Duration.ofSeconds(10))
                .until(() -> {
                    String status = jobRepository.findById(job.getId()).get().getStatus();
                    return "PARTIALLY_FAILED".equals(status) || "FAILED".equals(status);
                });

        BulkImportJob finalJob = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(finalJob.getProcessedRows()).isEqualTo(1);
        assertThat(finalJob.getFailedRows()).isEqualTo(1);
    }

    @Test
    void shouldEnforceIdempotency() {
        UUID sessionId = UUID.randomUUID();
        String csv = "variant_sku,warehouse_code,adjustment_type,quantity,reason\n" +
                     variantSku + "," + warehouseCode + ",RECEIVE,50,Idempotent test\n";

        MockMultipartFile file = new MockMultipartFile("file", "idem.csv", "text/csv", csv.getBytes());

        BulkImportJob job1 = bulkInventoryService.submitImport(sessionId, file);
        BulkImportJob job2 = bulkInventoryService.submitImport(sessionId, file);

        assertThat(job1.getId()).isEqualTo(job2.getId());
        assertThat(jobRepository.count()).isEqualTo(1);
    }
}
