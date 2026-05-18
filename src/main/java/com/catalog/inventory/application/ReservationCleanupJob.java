package com.catalog.inventory.application;

import com.catalog.inventory.domain.InventoryReservation;
import com.catalog.inventory.domain.InventoryJournal;
import com.catalog.inventory.domain.InventoryOperationType;
import com.catalog.inventory.domain.ActorType;
import com.catalog.inventory.event.InventoryUpdatedEvent;
import com.catalog.inventory.event.LowStockEvent;
import com.catalog.inventory.event.OutOfStockEvent;
import com.catalog.inventory.infrastructure.InventoryJournalRepository;
import com.catalog.inventory.infrastructure.InventoryRepository;
import com.catalog.inventory.infrastructure.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationCleanupJob {

    private final InventoryReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryJournalRepository journalRepository;
    private final PlatformTransactionManager transactionManager;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(cron = "${catalog.inventory.reservation-cleanup-cron:0 * * * * *}")
    public void releaseExpiredReservations() {
        List<UUID> expiredIds = reservationRepository.findExpiredReservations(Instant.now())
                .stream()
                .map(InventoryReservation::getId)
                .toList();

        if (expiredIds.isEmpty()) {
            return;
        }

        log.info("Reservation cleanup found {} expired reservations.", expiredIds.size());

        int released = 0;
        int failed = 0;
        for (UUID reservationId : expiredIds) {
            try {
                expireSingle(reservationId);
                released++;
            } catch (Exception ex) {
                failed++;
                log.error("Failed to expire reservation id={}: {}", reservationId, ex.getMessage(), ex);
            }
        }

        log.info("Reservation cleanup complete: released={} failed={}", released, failed);
    }

    public void expireSingle(UUID reservationId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> {
            reservationRepository.findActiveByIdWithLock(reservationId).ifPresent(reservation -> {
                if (!reservation.isExpired()) {
                    return;
                }

                int reservedBefore = reservation.getInventory().getReservedQuantity();
                reservation.getInventory().releaseReservation(reservation.getQuantity());
                inventoryRepository.save(reservation.getInventory());
                reservation.expire();
                reservationRepository.save(reservation);

                journalRepository.save(InventoryJournal.forReservationChange(
                        reservation.getInventory(),
                        InventoryOperationType.RESERVATION_RELEASE,
                        reservedBefore,
                        reservation.getId(),
                        ActorType.SCHEDULED_JOB
                ));

                publishStockEvents(reservation);
                log.debug("Expired reservation id={} ref={} qty={}", reservationId, reservation.getReferenceId(), reservation.getQuantity());
            });
        });
    }

    private void publishStockEvents(InventoryReservation reservation) {
        var inventory = reservation.getInventory();
        eventPublisher.publishEvent(new InventoryUpdatedEvent(
                inventory.getVariant().getId(),
                inventory.getVariant().getProduct().getId()));

        if (inventory.isOutOfStock()) {
            eventPublisher.publishEvent(new OutOfStockEvent(
                    inventory.getId(),
                    inventory.getVariant().getId(),
                    inventory.getWarehouse().getId()
            ));
            return;
        }

        if (inventory.isLowStock()) {
            eventPublisher.publishEvent(new LowStockEvent(
                    inventory.getId(),
                    inventory.getVariant().getId(),
                    inventory.getWarehouse().getId(),
                    inventory.getAvailableQuantity(),
                    inventory.getReorderLevel()
            ));
        }
    }
}
