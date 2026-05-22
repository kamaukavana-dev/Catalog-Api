package com.catalog.inventory.application;

import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.InsufficientStockException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.inventory.api.dto.request.AdjustStockRequest;
import com.catalog.inventory.api.dto.request.CreateInventoryRequest;
import com.catalog.inventory.api.dto.request.CreateReservationRequest;
import com.catalog.inventory.api.dto.response.InventoryResponse;
import com.catalog.inventory.api.dto.response.ReservationResponse;
import com.catalog.inventory.domain.Inventory;
import com.catalog.inventory.domain.InventoryJournal;
import com.catalog.inventory.domain.InventoryOperationType;
import com.catalog.inventory.domain.InventoryReservation;
import com.catalog.inventory.domain.ReservationStatus;
import com.catalog.inventory.event.InventoryUpdatedEvent;
import com.catalog.inventory.event.LowStockEvent;
import com.catalog.inventory.event.OutOfStockEvent;
import com.catalog.inventory.infrastructure.InventoryJournalRepository;
import com.catalog.inventory.infrastructure.InventoryRepository;
import com.catalog.inventory.infrastructure.InventoryReservationRepository;
import com.catalog.variant.domain.Variant;
import com.catalog.variant.infrastructure.VariantRepository;
import com.catalog.warehouse.domain.Warehouse;
import com.catalog.warehouse.infrastructure.WarehouseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryJournalRepository journalRepository;
    private final VariantRepository variantRepository;
    private final WarehouseRepository warehouseRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final com.catalog.common.observability.metrics.InventoryMetrics inventoryMetrics;
    private final int defaultReorderLevel;
    private final int reservationExpiryMinutes;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryReservationRepository reservationRepository,
            InventoryJournalRepository journalRepository,
            VariantRepository variantRepository,
            WarehouseRepository warehouseRepository,
            ApplicationEventPublisher eventPublisher,
            com.catalog.common.observability.metrics.InventoryMetrics inventoryMetrics,
            @Value("${catalog.inventory.default-reorder-level:10}") int defaultReorderLevel,
            @Value("${catalog.inventory.reservation-expiry-minutes:15}") int reservationExpiryMinutes) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.journalRepository = journalRepository;
        this.variantRepository = variantRepository;
        this.warehouseRepository = warehouseRepository;
        this.eventPublisher = eventPublisher;
        this.inventoryMetrics = inventoryMetrics;
        this.defaultReorderLevel = defaultReorderLevel;
        this.reservationExpiryMinutes = reservationExpiryMinutes;
    }

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttemptsExpression = "${catalog.inventory.retry-max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${catalog.inventory.retry-initial-delay-ms:50}",
                    multiplierExpression = "${catalog.inventory.retry-multiplier:2.0}"
            )
    )
    @Transactional
    public InventoryResponse createInventory(CreateInventoryRequest request) {
        Variant variant = variantRepository.findActiveById(request.variantId())
                .orElseThrow(() -> new ResourceNotFoundException("Variant", request.variantId()));
        Warehouse warehouse = warehouseRepository.findActiveById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", request.warehouseId()));

        if (inventoryRepository.existsByVariantAndWarehouse(request.variantId(), request.warehouseId())) {
            throw new BusinessRuleViolationException("Inventory record already exists for this variant at this warehouse.");
        }

        int reorderLevel = request.reorderLevel() > 0 ? request.reorderLevel() : defaultReorderLevel;
        Inventory inventory = Inventory.create(variant, warehouse, reorderLevel);
        int quantityBefore = inventory.getQuantity();
        int reservedBefore = inventory.getReservedQuantity();
        if (request.initialQuantity() > 0) {
            inventory.receiveStock(request.initialQuantity());
        }

        Inventory saved = inventoryRepository.save(inventory);
        if (request.initialQuantity() > 0) {
            journalRepository.save(InventoryJournal.forQuantityChange(
                saved, InventoryOperationType.INITIAL_STOCK,
                quantityBefore, reservedBefore,
                "INITIAL_STOCK", saved.getId(),
                com.catalog.inventory.domain.ActorType.SYSTEM, null, null
            ));
        }
        return toResponse(saved);
    }

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttemptsExpression = "${catalog.inventory.retry-max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${catalog.inventory.retry-initial-delay-ms:50}",
                    multiplierExpression = "${catalog.inventory.retry-multiplier:2.0}"
            )
    )
    @Transactional
    public InventoryResponse adjustStock(UUID inventoryId, AdjustStockRequest request) {
        Inventory inventory = findActiveInventoryOrThrow(inventoryId);
        int quantityBefore = inventory.getQuantity();
        int reservedBefore = inventory.getReservedQuantity();
        InventoryOperationType operationType;

        switch (request.type()) {
            case RECEIVE -> {
                inventory.receiveStock(request.amount());
                operationType = InventoryOperationType.RECEIVE;
            }
            case RECONCILE -> {
                inventory.reconcileQuantity(request.amount());
                operationType = InventoryOperationType.RECONCILIATION;
            }
            default -> throw new BusinessRuleViolationException("Unsupported adjustment type: " + request.type());
        }

        Inventory saved = inventoryRepository.save(inventory);
        journalRepository.save(InventoryJournal.forQuantityChange(
            saved, operationType,
            quantityBefore, reservedBefore,
            "ADJUSTMENT", saved.getId(),
            com.catalog.inventory.domain.ActorType.SYSTEM, null, null
        ));
        publishStockEvents(saved);
        return toResponse(saved);
    }

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttemptsExpression = "${catalog.inventory.retry-max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${catalog.inventory.retry-initial-delay-ms:50}",
                    multiplierExpression = "${catalog.inventory.retry-multiplier:2.0}"
            )
    )
    @Transactional
    public ReservationResponse reserveStock(CreateReservationRequest request) {
        long start = System.currentTimeMillis();
        inventoryMetrics.recordReservationAttempt();
        try {
            Inventory inventory = inventoryRepository.findActiveByVariantAndWarehouse(request.variantId(), request.warehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory", "variant=" + request.variantId() + ", warehouse=" + request.warehouseId()));

            InventoryReservation existing = reservationRepository
                    .findActiveByInventoryAndReferenceId(inventory.getId(), request.referenceId())
                    .orElse(null);
            if (existing != null) {
                inventoryMetrics.recordReservationSuccess(System.currentTimeMillis() - start);
                return toReservationResponse(existing);
            }

            int reservedBefore = inventory.getReservedQuantity(); // Snapshot BEFORE mutation

            inventory.reserve(request.quantity());
            inventoryRepository.save(inventory);

            Instant expiresAt = Instant.now().plus(reservationExpiryMinutes, ChronoUnit.MINUTES);
            InventoryReservation reservation = InventoryReservation.create(inventory, request.referenceId(), request.quantity(), expiresAt);
            reservationRepository.save(reservation);

            // Journal entry: reserved_quantity changed, physical quantity unchanged
            journalRepository.save(InventoryJournal.forReservationChange(
                inventory, InventoryOperationType.RESERVATION_CREATE,
                reservedBefore, reservation.getId(), com.catalog.inventory.domain.ActorType.SYSTEM
            ));

            publishStockEvents(inventory);
            ReservationResponse result = toReservationResponse(reservation);
            inventoryMetrics.recordReservationSuccess(System.currentTimeMillis() - start);
            return result;
        } catch (InsufficientStockException e) {
            inventoryMetrics.recordReservationFailure(System.currentTimeMillis() - start);
            throw e;
        }
    }

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttemptsExpression = "${catalog.inventory.retry-max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${catalog.inventory.retry-initial-delay-ms:50}",
                    multiplierExpression = "${catalog.inventory.retry-multiplier:2.0}"
            )
    )
    @Transactional
    public ReservationResponse completeReservation(UUID reservationId) {
        InventoryReservation reservation = reservationRepository.findActiveByIdWithLock(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", reservationId));

        Inventory inventory = reservation.getInventory();
        int quantityBefore = inventory.getQuantity();
        int reservedBefore = inventory.getReservedQuantity();

        inventory.completeSale(reservation.getQuantity());
        inventoryRepository.save(inventory);

        reservation.complete();
        reservationRepository.save(reservation);

        // SALE: both quantity and reserved change
        journalRepository.save(InventoryJournal.forQuantityChange(
            inventory, InventoryOperationType.SALE,
            quantityBefore, reservedBefore,
            "RESERVATION", reservation.getId(),
            com.catalog.inventory.domain.ActorType.SYSTEM, null, null
        ));

        publishStockEvents(inventory);
        return toReservationResponse(reservation);
    }

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttemptsExpression = "${catalog.inventory.retry-max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${catalog.inventory.retry-initial-delay-ms:50}",
                    multiplierExpression = "${catalog.inventory.retry-multiplier:2.0}"
            )
    )
    @Transactional
    public ReservationResponse cancelReservation(UUID reservationId) {
        InventoryReservation reservation = reservationRepository.findActiveByIdWithLock(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", reservationId));

        Inventory inventory = reservation.getInventory();
        int reservedBefore = inventory.getReservedQuantity();

        inventory.releaseReservation(reservation.getQuantity());
        inventoryRepository.save(inventory);

        reservation.cancel();
        reservationRepository.save(reservation);

        journalRepository.save(InventoryJournal.forReservationChange(
            inventory, InventoryOperationType.RESERVATION_RELEASE,
            reservedBefore, reservation.getId(), com.catalog.inventory.domain.ActorType.SYSTEM
        ));

        publishStockEvents(inventory);
        return toReservationResponse(reservation);
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getInventoryForVariant(UUID variantId) {
        return inventoryRepository.findActiveByVariantId(variantId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventory(UUID variantId, UUID warehouseId) {
        return toResponse(inventoryRepository.findActiveByVariantAndWarehouse(variantId, warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "variant=" + variantId + ", warehouse=" + warehouseId)));
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventoryById(UUID inventoryId) {
        return toResponse(findActiveInventoryOrThrow(inventoryId));
    }

    @Transactional(readOnly = true)
    public Page<InventoryJournal> getJournal(UUID inventoryId, int page, int size) {
        return journalRepository.findByInventoryId(inventoryId, PageRequest.of(page, size));
    }

    private Inventory findActiveInventoryOrThrow(UUID id) {
        return inventoryRepository.findById(id)
                .filter(inventory -> !inventory.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", id));
    }

    private void publishStockEvents(Inventory inventory) {
        eventPublisher.publishEvent(new InventoryUpdatedEvent(inventory.getVariant().getId(), inventory.getVariant().getProduct().getId()));
        if (inventory.isOutOfStock()) {
            eventPublisher.publishEvent(new OutOfStockEvent(inventory.getId(), inventory.getVariant().getId(), inventory.getWarehouse().getId()));
            return;
        }
        if (inventory.isLowStock()) {
            eventPublisher.publishEvent(new LowStockEvent(
                    inventory.getId(),
                    inventory.getVariant().getId(),
                    inventory.getWarehouse().getId(),
                    inventory.getAvailableQuantity(),
                    inventory.getReorderLevel()));
        }
    }

    private InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getVariant().getId(),
                inventory.getVariant().getInternalSku(),
                inventory.getWarehouse().getId(),
                inventory.getWarehouse().getCode(),
                inventory.getWarehouse().getName(),
                inventory.getQuantity(),
                inventory.getReservedQuantity(),
                inventory.getAvailableQuantity(),
                inventory.getReorderLevel(),
                inventory.isLowStock(),
                inventory.isOutOfStock(),
                inventory.getUpdatedAt());
    }

    private ReservationResponse toReservationResponse(InventoryReservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getInventory().getId(),
                reservation.getReferenceId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getReleasedAt(),
                reservation.getCreatedAt());
    }
}
