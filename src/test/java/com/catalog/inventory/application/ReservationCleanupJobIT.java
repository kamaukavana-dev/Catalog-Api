package com.catalog.inventory.application;

import com.catalog.brand.domain.Brand;
import com.catalog.brand.infrastructure.BrandRepository;
import com.catalog.category.domain.Category;
import com.catalog.category.infrastructure.CategoryRepository;
import com.catalog.common.BaseIntegrationTest;
import com.catalog.inventory.domain.ActorType;
import com.catalog.inventory.domain.Inventory;
import com.catalog.inventory.domain.InventoryJournal;
import com.catalog.inventory.domain.InventoryOperationType;
import com.catalog.inventory.domain.InventoryReservation;
import com.catalog.inventory.domain.ReservationStatus;
import com.catalog.inventory.infrastructure.InventoryJournalRepository;
import com.catalog.inventory.infrastructure.InventoryRepository;
import com.catalog.inventory.infrastructure.InventoryReservationRepository;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationCleanupJobIT extends BaseIntegrationTest {

    @Autowired private ReservationCleanupJob cleanupJob;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private InventoryReservationRepository reservationRepository;
    @Autowired private InventoryJournalRepository journalRepository;

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private VariantRepository variantRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        journalRepository.deleteAll();
        reservationRepository.deleteAll();
        inventoryRepository.deleteAll();
        variantRepository.deleteAll();
        productRepository.deleteAll();
        brandRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
    }

    @Test
    void shouldExpireReservationAndReleaseReservedQuantity_whenReservationIsExpired() {
        UUID variantId = seedVariant("SKU-RSV-EXP");
        Warehouse wh = warehouseRepository.save(Warehouse.create("WH-RSV", "RSV Warehouse", WarehouseType.MAIN));

        Inventory inv = Inventory.create(variantRepository.findById(variantId).orElseThrow(), wh, 0);
        inv.receiveStock(10);
        inv.reserve(3);
        inv = inventoryRepository.save(inv);

        InventoryReservation r = InventoryReservation.create(inv, UUID.randomUUID(), 3,
                Instant.now().minus(5, ChronoUnit.MINUTES));
        r = reservationRepository.save(r);

        cleanupJob.releaseExpiredReservations();

        Inventory refreshedInv = inventoryRepository.findById(inv.getId()).orElseThrow();
        InventoryReservation refreshedR = reservationRepository.findById(r.getId()).orElseThrow();

        assertThat(refreshedInv.getReservedQuantity()).isEqualTo(0);
        assertThat(refreshedR.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(refreshedR.getReleasedAt()).isNotNull();

        List<InventoryJournal> journals = journalRepository.findAll();
        assertThat(journals).hasSize(1);
        InventoryJournal j = journals.get(0);
        assertThat(j.getOperationType()).isEqualTo(InventoryOperationType.RESERVATION_RELEASE);
        assertThat(j.getActorType()).isEqualTo(ActorType.SCHEDULED_JOB);
        assertThat(j.getReferenceId()).isEqualTo(r.getId());
    }

    @Test
    void shouldNotExpireReservation_whenReservationExpiresInFuture() {
        UUID variantId = seedVariant("SKU-RSV-FUT");
        Warehouse wh = warehouseRepository.save(Warehouse.create("WH-RSV", "RSV Warehouse", WarehouseType.MAIN));

        Inventory inv = Inventory.create(variantRepository.findById(variantId).orElseThrow(), wh, 0);
        inv.receiveStock(10);
        inv.reserve(2);
        inv = inventoryRepository.save(inv);

        InventoryReservation r = InventoryReservation.create(inv, UUID.randomUUID(), 2,
                Instant.now().plus(30, ChronoUnit.MINUTES));
        r = reservationRepository.save(r);

        cleanupJob.releaseExpiredReservations();

        Inventory refreshedInv = inventoryRepository.findById(inv.getId()).orElseThrow();
        InventoryReservation refreshedR = reservationRepository.findById(r.getId()).orElseThrow();

        assertThat(refreshedInv.getReservedQuantity()).isEqualTo(2);
        assertThat(refreshedR.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(journalRepository.findAll()).isEmpty();
    }

    @Test
    void shouldBeIdempotent_whenCleanupRunsTwice() {
        UUID variantId = seedVariant("SKU-RSV-IDEMP");
        Warehouse wh = warehouseRepository.save(Warehouse.create("WH-RSV", "RSV Warehouse", WarehouseType.MAIN));

        Inventory inv = Inventory.create(variantRepository.findById(variantId).orElseThrow(), wh, 0);
        inv.receiveStock(10);
        inv.reserve(1);
        inv = inventoryRepository.save(inv);

        InventoryReservation r = InventoryReservation.create(inv, UUID.randomUUID(), 1,
                Instant.now().minus(1, ChronoUnit.HOURS));
        r = reservationRepository.save(r);

        cleanupJob.releaseExpiredReservations();
        cleanupJob.releaseExpiredReservations();

        Inventory refreshedInv = inventoryRepository.findById(inv.getId()).orElseThrow();
        InventoryReservation refreshedR = reservationRepository.findById(r.getId()).orElseThrow();

        assertThat(refreshedInv.getReservedQuantity()).isEqualTo(0);
        assertThat(refreshedR.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(journalRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldNotThrow_whenReservationDoesNotExist() {
        cleanupJob.expireSingle(UUID.randomUUID());
        assertThat(journalRepository.findAll()).isEmpty();
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

