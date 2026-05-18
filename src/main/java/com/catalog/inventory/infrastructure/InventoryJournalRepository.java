package com.catalog.inventory.infrastructure;

import com.catalog.inventory.domain.InventoryJournal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Append-only repository.
 *
 * Notice what's MISSING: no update methods, no delete methods.
 * Spring Data's inherited save(T entity) technically exists but only
 * insert works — the DB privilege REVOKE prevents UPDATE at the DB level.
 *
 * Explicitly documented: do not call save() on existing journal entries.
 */
public interface InventoryJournalRepository extends JpaRepository<InventoryJournal, UUID> {

    @Query("SELECT j FROM InventoryJournal j WHERE j.inventoryId = :inventoryId " +
           "ORDER BY j.createdAt DESC")
    Page<InventoryJournal> findByInventoryId(
            @Param("inventoryId") UUID inventoryId, Pageable pageable);

    @Query("SELECT j FROM InventoryJournal j WHERE j.variantId = :variantId " +
           "ORDER BY j.createdAt DESC")
    Page<InventoryJournal> findByVariantId(
            @Param("variantId") UUID variantId, Pageable pageable);

    // Reconstruct both sides of a transfer by reference ID
    @Query("SELECT j FROM InventoryJournal j WHERE j.referenceId = :transferId " +
           "AND j.operationType IN ('TRANSFER_OUT', 'TRANSFER_IN') " +
           "ORDER BY j.createdAt ASC")
    List<InventoryJournal> findTransferJournalByReferenceId(
            @Param("transferId") UUID transferId);

    // Financial reconciliation: all SALE and RECEIVE operations in a date range
    @Query("SELECT j FROM InventoryJournal j WHERE j.operationType IN :operations " +
           "AND j.createdAt BETWEEN :from AND :to ORDER BY j.createdAt ASC")
    List<InventoryJournal> findByOperationsInDateRange(
            @Param("operations") List<com.catalog.inventory.domain.InventoryOperationType> operations,
            @Param("from") Instant from,
            @Param("to") Instant to);
}

