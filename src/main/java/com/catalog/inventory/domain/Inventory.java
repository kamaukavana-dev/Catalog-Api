package com.catalog.inventory.domain;

import com.catalog.common.audit.BaseEntity;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.InsufficientStockException;
import com.catalog.variant.domain.Variant;
import com.catalog.warehouse.domain.Warehouse;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
public class Inventory extends BaseEntity {

    // @Version is inherited from BaseEntity.
    // Every UPDATE to this entity auto-increments the version column.
    // Concurrent writers get ObjectOptimisticLockingFailureException.
    // The service layer retries on this exception via @Retryable.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false, updatable = false)
    private Variant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    private Warehouse warehouse;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------
    public static Inventory create(Variant variant, Warehouse warehouse, int reorderLevel) {
        Inventory inv = new Inventory();
        inv.variant = variant;
        inv.warehouse = warehouse;
        inv.quantity = 0;
        inv.reservedQuantity = 0;
        inv.reorderLevel = reorderLevel;
        return inv;
    }

    // -------------------------------------------------------------------------
    // Core domain: never stored, always computed
    // -------------------------------------------------------------------------
    public int getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    public boolean isLowStock() {
        return reorderLevel > 0 && getAvailableQuantity() <= reorderLevel;
    }

    public boolean isOutOfStock() {
        return getAvailableQuantity() <= 0;
    }

    // -------------------------------------------------------------------------
    // Stock operations — domain methods enforce all business rules.
    // Service layer calls these. Direct field mutation via setQuantity() is banned
    // for operational writes — only for JPA hydration.
    // -------------------------------------------------------------------------

    /**
     * Receive incoming stock (purchase order, supplier delivery).
     * Increases physical quantity. Never touches reservedQuantity.
     */
    public void receiveStock(int amount) {
        if (amount <= 0) {
            throw new BusinessRuleViolationException(
                "Stock receive amount must be positive. Got: " + amount);
        }
        this.quantity += amount;
    }

    /**
     * Reconcile physical count (stocktake).
     * Sets quantity to an absolute value. Cannot set below reservedQuantity.
     */
    public void reconcileQuantity(int physicalCount) {
        if (physicalCount < 0) {
            throw new BusinessRuleViolationException(
                "Physical count cannot be negative. Got: " + physicalCount);
        }
        if (physicalCount < this.reservedQuantity) {
            throw new BusinessRuleViolationException(
                String.format("Cannot reconcile quantity to %d: %d units are reserved. " +
                              "Release or complete reservations first.",
                              physicalCount, this.reservedQuantity));
        }
        this.quantity = physicalCount;
    }

    /**
     * Reserve stock for a checkout. Increments reservedQuantity.
     * Called ONLY by InventoryService.reserveStock().
     * DB constraint CHECK(reserved_quantity <= quantity) is the safety net.
     */
    public void reserve(int amount) {
        if (amount <= 0) {
            throw new BusinessRuleViolationException("Reservation amount must be positive.");
        }
        if (getAvailableQuantity() < amount) {
            throw new InsufficientStockException(
                variant.getId(), warehouse.getId(), amount, getAvailableQuantity());
        }
        this.reservedQuantity += amount;
    }

    /**
     * Release a reservation (expiry, cancellation).
     * Decrements reservedQuantity only. Physical stock unchanged.
     */
    public void releaseReservation(int amount) {
        if (amount <= 0) {
            throw new BusinessRuleViolationException("Release amount must be positive.");
        }
        if (amount > this.reservedQuantity) {
            throw new BusinessRuleViolationException(
                "Cannot release more than reserved. reserved=" + this.reservedQuantity + ", amount=" + amount);
        }
        this.reservedQuantity -= amount;
    }

    /**
     * Complete a sale. Decrements both quantity AND reservedQuantity.
     * The stock was reserved during checkout; now it's sold.
     */
    public void completeSale(int amount) {
        if (amount <= 0) {
            throw new BusinessRuleViolationException("Sale amount must be positive.");
        }
        if (this.quantity < amount) {
            throw new BusinessRuleViolationException(
                "Cannot complete sale: insufficient quantity.");
        }
        if (this.reservedQuantity < amount) {
            throw new BusinessRuleViolationException(
                "Cannot complete sale: reserved quantity is lower than sale amount.");
        }
        this.quantity -= amount;
        this.reservedQuantity -= amount;
    }

    /**
     * Remove stock for a warehouse-to-warehouse transfer.
     * Only affects available (non-reserved) quantity.
     * Reserved stock cannot be transferred — it's committed to active checkouts.
     */
    public void transferOut(int amount) {
        if (amount <= 0) {
            throw new BusinessRuleViolationException("Transfer amount must be positive.");
        }
        if (getAvailableQuantity() < amount) {
            throw new InsufficientStockException(
                getVariant().getId(), getWarehouse().getId(), amount, getAvailableQuantity());
        }
        this.quantity -= amount;
    }

    /**
     * Receive stock from a warehouse-to-warehouse transfer.
     */
    public void transferIn(int amount) {
        if (amount <= 0) {
            throw new BusinessRuleViolationException("Transfer amount must be positive.");
        }
        this.quantity += amount;
    }
}
