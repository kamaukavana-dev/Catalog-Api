package com.catalog.inventory.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class InventoryEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLowStock(LowStockEvent event) {
        log.warn("LOW_STOCK_ALERT inventoryId={} variantId={} warehouseId={} available={} threshold={}",
                event.inventoryId(), event.variantId(), event.warehouseId(),
                event.availableQuantity(), event.reorderLevel());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutOfStock(OutOfStockEvent event) {
        log.warn("OUT_OF_STOCK inventoryId={} variantId={} warehouseId={}",
                event.inventoryId(), event.variantId(), event.warehouseId());
    }
}

