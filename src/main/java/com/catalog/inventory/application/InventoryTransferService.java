package com.catalog.inventory.application;

import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.inventory.api.dto.request.TransferStockRequest;
import com.catalog.inventory.api.dto.response.TransferResponse;
import com.catalog.inventory.domain.*;
import com.catalog.inventory.infrastructure.InventoryJournalRepository;
import com.catalog.inventory.infrastructure.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryTransferService {

    private final InventoryRepository inventoryRepository;
    private final InventoryJournalRepository journalRepository;
    private final com.catalog.common.observability.metrics.InventoryMetrics inventoryMetrics;

    /**
     * Atomic warehouse-to-warehouse stock transfer.
     *
     * Concurrency strategy: PESSIMISTIC_WRITE (SELECT FOR UPDATE).
     * Justification: transfers are operational, low-throughput, admin-initiated.
     * High consistency requirement outweighs throughput concern here.
     * Contrast with checkout reservations: high-throughput → optimistic locking.
     *
     * Deadlock prevention: acquire locks in deterministic order (smaller UUID first).
     * Without this: Transfer(A→B) and Transfer(B→A) simultaneously = deadlock.
     */
    @Transactional
    public TransferResponse transfer(TransferStockRequest request) {
        try {
            validateTransferRequest(request);

            // Acquire pessimistic locks in deterministic order.
            // UUID.compareTo() provides consistent lexicographic ordering.
            // ALWAYS lock the inventory with the smaller UUID first.
            UUID sourceId = request.sourceInventoryId();
            UUID destinationId = request.destinationInventoryId();
            boolean sourceFirst = sourceId.compareTo(destinationId) < 0;
            UUID firstLockId = sourceFirst ? sourceId : destinationId;
            UUID secondLockId = sourceFirst ? destinationId : sourceId;

            Inventory lockedFirst  = inventoryRepository.findByIdWithLock(firstLockId)
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory", firstLockId));
            Inventory lockedSecond = inventoryRepository.findByIdWithLock(secondLockId)
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory", secondLockId));

            // Re-assign to semantic variables after locking
            Inventory lockedSource = sourceFirst ? lockedFirst : lockedSecond;
            Inventory lockedDest   = sourceFirst ? lockedSecond : lockedFirst;

            // Validate same variant — cannot transfer between different products
            if (!lockedSource.getVariant().getId().equals(lockedDest.getVariant().getId())) {
                throw new BusinessRuleViolationException(
                    "Source and destination inventory must reference the same variant. " +
                    "Cannot transfer between different products.");
            }

            // Validate different warehouses
            if (lockedSource.getWarehouse().getId().equals(lockedDest.getWarehouse().getId())) {
                throw new BusinessRuleViolationException(
                    "Source and destination cannot be the same warehouse.");
            }

            // Snapshot pre-transfer state for journal
            int sourceQtyBefore   = lockedSource.getQuantity();
            int sourceResvBefore  = lockedSource.getReservedQuantity();
            int destQtyBefore     = lockedDest.getQuantity();
            int destResvBefore    = lockedDest.getReservedQuantity();

            // Domain method validates available quantity and throws InsufficientStockException
            lockedSource.transferOut(request.quantity());
            lockedDest.transferIn(request.quantity());

            inventoryRepository.save(lockedSource);
            inventoryRepository.save(lockedDest);

            // Dual journal entries linked by a single transfer reference UUID
            UUID transferRef = UUID.randomUUID();

            journalRepository.save(InventoryJournal.forQuantityChange(
                lockedSource, InventoryOperationType.TRANSFER_OUT,
                sourceQtyBefore, sourceResvBefore,
                "TRANSFER", transferRef,
                ActorType.ADMIN_USER, null, request.reason()
            ));

            journalRepository.save(InventoryJournal.forQuantityChange(
                lockedDest, InventoryOperationType.TRANSFER_IN,
                destQtyBefore, destResvBefore,
                "TRANSFER", transferRef,
                ActorType.ADMIN_USER, null, request.reason()
            ));

            inventoryMetrics.recordTransfer();
            log.info("Transfer complete: ref={} from={}({}) to={}({}) qty={}",
                    transferRef,
                    lockedSource.getWarehouse().getCode(), request.sourceInventoryId(),
                    lockedDest.getWarehouse().getCode(), request.destinationInventoryId(),
                    request.quantity());

            return new TransferResponse(
                transferRef,
                request.sourceInventoryId(), lockedSource.getAvailableQuantity(),
                request.destinationInventoryId(), lockedDest.getAvailableQuantity(),
                request.quantity()
            );
        } catch (RuntimeException ex) {
            inventoryMetrics.recordTransferFailure();
            throw ex;
        }
    }

    private void validateTransferRequest(TransferStockRequest request) {
        if (request.quantity() <= 0) {
            throw new BusinessRuleViolationException("Transfer quantity must be positive.");
        }
        if (request.sourceInventoryId().equals(request.destinationInventoryId())) {
            throw new BusinessRuleViolationException("Source and destination cannot be identical.");
        }
    }

}
