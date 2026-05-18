package com.catalog.inventory.domain;

import com.catalog.common.audit.BaseEntity;
import com.catalog.common.exception.BusinessRuleViolationException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_reservations")
@Getter
@Setter
@NoArgsConstructor
public class InventoryReservation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id", nullable = false, updatable = false)
    private Inventory inventory;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    public static InventoryReservation create(Inventory inventory, UUID referenceId,
                                               int quantity, Instant expiresAt) {
        InventoryReservation r = new InventoryReservation();
        r.inventory = inventory;
        r.referenceId = referenceId;
        r.quantity = quantity;
        r.status = ReservationStatus.ACTIVE;
        r.expiresAt = expiresAt;
        return r;
    }

    public void complete() {
        assertActive("complete");
        this.status = ReservationStatus.COMPLETED;
        this.releasedAt = Instant.now();
    }

    public void cancel() {
        assertActive("cancel");
        this.status = ReservationStatus.CANCELLED;
        this.releasedAt = Instant.now();
    }

    public void expire() {
        assertActive("expire");
        this.status = ReservationStatus.EXPIRED;
        this.releasedAt = Instant.now();
    }

    public boolean isExpired() {
        return status == ReservationStatus.ACTIVE && Instant.now().isAfter(expiresAt);
    }

    private void assertActive(String operation) {
        if (this.status != ReservationStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                String.format("Cannot %s a reservation in status %s. " +
                              "Only ACTIVE reservations can be %sd.",
                              operation, this.status, operation));
        }
    }
}

