package com.catalog.inventory.domain;

import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.InsufficientStockException;
import com.catalog.variant.domain.Variant;
import com.catalog.warehouse.domain.Warehouse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryDomainTest {

    private Inventory buildInventory(int qty, int reserved) {
        Variant variant = mock(Variant.class);
        Warehouse warehouse = mock(Warehouse.class);
        when(variant.getId()).thenReturn(UUID.randomUUID());
        when(warehouse.getId()).thenReturn(UUID.randomUUID());

        Inventory inventory = Inventory.create(variant, warehouse, 5);
        inventory.setQuantity(qty);
        inventory.setReservedQuantity(reserved);
        return inventory;
    }

    @Test
    void availableQuantityIsComputed() {
        Inventory inventory = buildInventory(100, 30);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(70);
    }

    @Test
    void reserveRejectsInsufficientStock() {
        Inventory inventory = buildInventory(5, 3);
        assertThatThrownBy(() -> inventory.reserve(3))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void reconcileRejectsCountsBelowReserved() {
        Inventory inventory = buildInventory(10, 5);
        assertThatThrownBy(() -> inventory.reconcileQuantity(3))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    void releaseReservationRejectsOverRelease() {
        Inventory inventory = buildInventory(10, 2);
        assertThatThrownBy(() -> inventory.releaseReservation(3))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Cannot release more than reserved");
    }

    @Test
    void completeSaleRejectsWhenReservedIsTooLow() {
        Inventory inventory = buildInventory(10, 1);
        assertThatThrownBy(() -> inventory.completeSale(2))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("reserved quantity is lower");
    }
}
