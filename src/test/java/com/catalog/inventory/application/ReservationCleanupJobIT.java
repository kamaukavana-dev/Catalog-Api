package com.catalog.inventory.application;

import com.catalog.common.BaseIntegrationTest;
import com.catalog.inventory.domain.*;
import com.catalog.inventory.infrastructure.InventoryJournalRepository;
import com.catalog.inventory.infrastructure.InventoryRepository;
import com.catalog.inventory.infrastructure.InventoryReservationRepository;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ReservationCleanupJobIT extends BaseIntegrationTest {

    @Autowired
    private ReservationCleanupJob cleanupJob;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryJournalRepository journalRepository;

    @Autowired
    private VariantRepository variantRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductRepository productRepository;

    private Inventory inventory;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        journalRepository.deleteAll();
        inventoryRepository.deleteAll();
        variantRepository.deleteAll();
        productRepository.deleteAll();
        warehouseRepository.deleteAll();

        Product product = productRepository.save(Product.createDraft("Res Product", "res-prod-" + UUID.randomUUID()));
        Variant variant = variantRepository.save(Variant.createDraft(product, "SKU-RES-" + UUID.randomUUID(), new BigDecimal("10.00"), TaxClass.STANDARD));
        Warehouse warehouse = warehouseRepository.save(Warehouse.create("WH-RES", "Res WH", WarehouseType.MAIN));
        inventory = Inventory.create(variant, warehouse, 5);
        inventory.receiveStock(100);
        inventory = inventoryRepository.save(inventory);
    }

    @Test
    void shouldExpireReservations_whenExpired() {
        // Given: one expired and one active reservation
        UUID refExpired = UUID.randomUUID();
        InventoryReservation expired = InventoryReservation.create(inventory, refExpired, 10, Instant.now().minus(1, ChronoUnit.HOURS));
        inventory.reserve(10);
        inventory = inventoryRepository.save(inventory);
        expired = reservationRepository.save(expired);

        UUID refActive = UUID.randomUUID();
        InventoryReservation active = InventoryReservation.create(inventory, refActive, 5, Instant.now().plus(1, ChronoUnit.HOURS));
        inventory.reserve(5);
        inventory = inventoryRepository.save(inventory);
        active = reservationRepository.save(active);

        // When: cleanup job runs
        cleanupJob.releaseExpiredReservations();

        // Then: expired reservation is marked EXPIRED, active remains ACTIVE
        InventoryReservation finalExpired = reservationRepository.findById(expired.getId()).orElseThrow();
        assertThat(finalExpired.getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        InventoryReservation finalActive = reservationRepository.findById(active.getId()).orElseThrow();
        assertThat(finalActive.getStatus()).isEqualTo(ReservationStatus.ACTIVE);

        // And: inventory reserved quantity is updated (only active reservation holds stock)
        Inventory finalInv = inventoryRepository.findById(inventory.getId()).orElseThrow();
        assertThat(finalInv.getReservedQuantity()).isEqualTo(5);
    }
}
