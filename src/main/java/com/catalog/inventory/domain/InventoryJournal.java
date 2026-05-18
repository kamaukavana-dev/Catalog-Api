package com.catalog.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only inventory audit record.
 *
 * Design decisions:
 * 1. Does NOT extend BaseEntity — no version, no updatedAt, no soft delete.
 *    Journal entries are immutable by definition.
 * 2. Denormalizes variant_id and warehouse_id — remains queryable even
 *    if the inventory record is soft-deleted.
 * 3. DB-level: REVOKE UPDATE, DELETE on the application DB user (see migration V8).
 * 4. Application-level: JournalRepository has NO save(entity) for existing records.
 *    Inserts only.
 */
@Entity
@Table(name = "inventory_journal")
@Getter
@NoArgsConstructor
public class InventoryJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "inventory_id", updatable = false, nullable = false)
    private UUID inventoryId;

    @Column(name = "variant_id", updatable = false, nullable = false)
    private UUID variantId;

    @Column(name = "warehouse_id", updatable = false, nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", updatable = false, nullable = false, length = 30)
    private InventoryOperationType operationType;

    @Column(name = "quantity_before", updatable = false, nullable = false)
    private int quantityBefore;

    @Column(name = "quantity_after", updatable = false, nullable = false)
    private int quantityAfter;

    @Column(name = "quantity_delta", updatable = false, nullable = false)
    private int quantityDelta;

    @Column(name = "reserved_before", updatable = false, nullable = false)
    private int reservedBefore;

    @Column(name = "reserved_after", updatable = false, nullable = false)
    private int reservedAfter;

    @Column(name = "reserved_delta", updatable = false, nullable = false)
    private int reservedDelta;

    @Column(name = "reference_type", updatable = false, length = 50)
    private String referenceType;

    @Column(name = "reference_id", updatable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", updatable = false, nullable = false, length = 30)
    private ActorType actorType;

    @Column(name = "actor_id", updatable = false, length = 200)
    private String actorId;

    @Column(name = "reason", updatable = false, columnDefinition = "TEXT")
    private String reason;

    @PrePersist
    void onPersist() {
        this.createdAt = Instant.now();
        // Validate tamper-detection invariants before persisting
        if (this.quantityDelta != this.quantityAfter - this.quantityBefore) {
            throw new IllegalStateException(
                "Journal integrity violation: quantity_delta != quantity_after - quantity_before");
        }
        if (this.reservedDelta != this.reservedAfter - this.reservedBefore) {
            throw new IllegalStateException(
                "Journal integrity violation: reserved_delta != reserved_after - reserved_before");
        }
    }

    // -------------------------------------------------------------------------
    // Static factory methods — one per operation type for clarity
    // -------------------------------------------------------------------------

    public static InventoryJournal forQuantityChange(
            Inventory inventory,
            InventoryOperationType operationType,
            int quantityBefore, int reservedBefore,
            String referenceType, UUID referenceId,
            ActorType actorType, String actorId, String reason) {

        InventoryJournal j = new InventoryJournal();
        j.inventoryId = inventory.getId();
        j.variantId = inventory.getVariant().getId();
        j.warehouseId = inventory.getWarehouse().getId();
        j.operationType = operationType;
        j.quantityBefore = quantityBefore;
        j.quantityAfter = inventory.getQuantity();
        j.quantityDelta = inventory.getQuantity() - quantityBefore;
        j.reservedBefore = reservedBefore;
        j.reservedAfter = inventory.getReservedQuantity();
        j.reservedDelta = inventory.getReservedQuantity() - reservedBefore;
        j.referenceType = referenceType;
        j.referenceId = referenceId;
        j.actorType = actorType;
        j.actorId = actorId;
        j.reason = reason;
        return j;
    }

    // Convenience: operations that only change reserved quantity (reservation create/release)
    public static InventoryJournal forReservationChange(
            Inventory inventory,
            InventoryOperationType operationType,
            int reservedBefore,
            UUID reservationId,
            ActorType actorType) {

        InventoryJournal j = new InventoryJournal();
        j.inventoryId = inventory.getId();
        j.variantId = inventory.getVariant().getId();
        j.warehouseId = inventory.getWarehouse().getId();
        j.operationType = operationType;
        j.quantityBefore = inventory.getQuantity();
        j.quantityAfter = inventory.getQuantity();  // unchanged
        j.quantityDelta = 0;
        j.reservedBefore = reservedBefore;
        j.reservedAfter = inventory.getReservedQuantity();
        j.reservedDelta = inventory.getReservedQuantity() - reservedBefore;
        j.referenceType = "RESERVATION";
        j.referenceId = reservationId;
        j.actorType = actorType;
        return j;
    }
}
