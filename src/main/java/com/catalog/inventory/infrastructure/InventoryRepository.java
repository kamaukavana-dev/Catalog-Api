package com.catalog.inventory.infrastructure;

import com.catalog.inventory.domain.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Query("SELECT i FROM Inventory i JOIN FETCH i.variant JOIN FETCH i.warehouse WHERE i.variant.id = :variantId AND i.deletedAt IS NULL")
    List<Inventory> findActiveByVariantId(@Param("variantId") UUID variantId);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.variant JOIN FETCH i.warehouse WHERE i.variant.id = :variantId AND i.warehouse.id = :warehouseId AND i.deletedAt IS NULL")
    Optional<Inventory> findActiveByVariantAndWarehouse(@Param("variantId") UUID variantId,
                                                        @Param("warehouseId") UUID warehouseId);

    @Query("SELECT COUNT(i) > 0 FROM Inventory i WHERE i.variant.id = :variantId AND i.warehouse.id = :warehouseId AND i.deletedAt IS NULL")
    boolean existsByVariantAndWarehouse(@Param("variantId") UUID variantId,
                                        @Param("warehouseId") UUID warehouseId);

    @Query("SELECT i FROM Inventory i WHERE i.deletedAt IS NULL AND i.reorderLevel > 0 AND (i.quantity - i.reservedQuantity) <= i.reorderLevel")
    List<Inventory> findAllLowStock();

    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.deletedAt IS NULL AND i.reorderLevel > 0 AND (i.quantity - i.reservedQuantity) <= i.reorderLevel")
    long countLowStock();

    @Query("SELECT COALESCE(SUM(i.quantity - i.reservedQuantity), 0) FROM Inventory i WHERE i.variant.id = :variantId AND i.deletedAt IS NULL")
    int getTotalAvailableQuantityForVariant(@Param("variantId") UUID variantId);

    @Query("SELECT COUNT(i) > 0 FROM Inventory i WHERE i.variant.id = :variantId AND i.quantity > 0 AND i.deletedAt IS NULL")
    boolean hasStockForVariant(@Param("variantId") UUID variantId);

    /**
     * Acquires a PESSIMISTIC_WRITE (SELECT FOR UPDATE) lock on the inventory row.
     * Used exclusively for warehouse transfers where two rows must be locked
     * in deterministic order to prevent deadlocks.
     *
     * Not used for reservation operations — those use optimistic locking (@Version).
     * Transfers are low-throughput, high-consistency operations: pessimistic is correct here.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.id = :id AND i.deletedAt IS NULL")
    Optional<Inventory> findByIdWithLock(@Param("id") UUID id);

    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.reservedQuantity > i.quantity")
    long countViolatingReservedConstraint();
}
