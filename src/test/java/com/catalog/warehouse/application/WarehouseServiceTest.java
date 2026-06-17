package com.catalog.warehouse.application;

import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.inventory.infrastructure.InventoryRepository;
import com.catalog.warehouse.api.dto.request.CreateWarehouseRequest;
import com.catalog.warehouse.api.dto.request.UpdateWarehouseRequest;
import com.catalog.warehouse.api.dto.response.WarehouseResponse;
import com.catalog.warehouse.domain.Warehouse;
import com.catalog.warehouse.domain.WarehouseType;
import com.catalog.warehouse.infrastructure.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock private WarehouseRepository warehouseRepository;
    @Mock private InventoryRepository inventoryRepository;

    @InjectMocks private WarehouseService warehouseService;

    @Test
    void shouldCreateWarehouse() {
        CreateWarehouseRequest req = new CreateWarehouseRequest("WH1", "Warehouse 1", WarehouseType.MAIN, "Addr", "City", "US");
        when(warehouseRepository.existsByCode("WH1")).thenReturn(false);
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(i -> {
            Warehouse w = i.getArgument(0);
            return w;
        });

        WarehouseResponse resp = warehouseService.createWarehouse(req);

        assertThat(resp.code()).isEqualTo("WH1");
        verify(warehouseRepository).save(any(Warehouse.class));
    }

    @Test
    void shouldThrowDuplicate_whenCodeExists() {
        CreateWarehouseRequest req = new CreateWarehouseRequest("WH1", "Warehouse 1", WarehouseType.MAIN, null, null, null);
        when(warehouseRepository.existsByCode("WH1")).thenReturn(true);

        assertThatThrownBy(() -> warehouseService.createWarehouse(req))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void shouldDeactivateWarehouse_whenNoInventory() {
        UUID id = UUID.randomUUID();
        Warehouse wh = Warehouse.create("WH1", "N", WarehouseType.MAIN);
        wh.setId(id);
        when(warehouseRepository.findActiveById(id)).thenReturn(Optional.of(wh));
        when(inventoryRepository.existsActiveByWarehouseId(id)).thenReturn(false);
        when(warehouseRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        warehouseService.updateWarehouse(id, new UpdateWarehouseRequest(null, null, null, null, null, false));

        assertThat(wh.isActive()).isFalse();
    }

    @Test
    void shouldThrowViolation_whenDeactivatingWithInventory() {
        UUID id = UUID.randomUUID();
        Warehouse wh = Warehouse.create("WH1", "N", WarehouseType.MAIN);
        wh.setId(id);
        wh.setActive(true);
        when(warehouseRepository.findActiveById(id)).thenReturn(Optional.of(wh));
        when(inventoryRepository.existsActiveByWarehouseId(id)).thenReturn(true);

        assertThatThrownBy(() -> warehouseService.updateWarehouse(id, new UpdateWarehouseRequest(null, null, null, null, null, false)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
