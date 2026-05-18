package com.catalog.inventory.infrastructure;

import com.catalog.inventory.domain.InventoryReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {

    @Query("SELECT r FROM InventoryReservation r WHERE r.id = :id AND r.status = 'ACTIVE'")
    Optional<InventoryReservation> findActiveById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM InventoryReservation r JOIN FETCH r.inventory i WHERE r.id = :id AND r.status = 'ACTIVE'")
    Optional<InventoryReservation> findActiveByIdWithLock(@Param("id") UUID id);

    @Query("SELECT r FROM InventoryReservation r JOIN FETCH r.inventory i WHERE r.status = 'ACTIVE' AND r.expiresAt < :now ORDER BY r.expiresAt ASC")
    List<InventoryReservation> findExpiredReservations(@Param("now") Instant now);

    @Query("SELECT r FROM InventoryReservation r WHERE r.referenceId = :referenceId AND r.status = 'ACTIVE'")
    List<InventoryReservation> findActiveByReferenceId(@Param("referenceId") UUID referenceId);

    @Query("SELECT r FROM InventoryReservation r WHERE r.inventory.id = :inventoryId AND r.referenceId = :referenceId AND r.status = 'ACTIVE'")
    Optional<InventoryReservation> findActiveByInventoryAndReferenceId(
            @Param("inventoryId") UUID inventoryId,
            @Param("referenceId") UUID referenceId);
}
