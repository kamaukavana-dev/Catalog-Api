package com.catalog.warehouse.application;

import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.common.exception.ResourceNotFoundException;
import com.catalog.warehouse.api.dto.request.CreateWarehouseRequest;
import com.catalog.warehouse.api.dto.request.UpdateWarehouseRequest;
import com.catalog.warehouse.api.dto.response.WarehouseResponse;
import com.catalog.warehouse.domain.Warehouse;
import com.catalog.warehouse.infrastructure.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    @Transactional
    public WarehouseResponse createWarehouse(CreateWarehouseRequest request) {
        String code = request.code().trim().toUpperCase();
        if (warehouseRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Warehouse code '" + code + "' already exists.");
        }

        Warehouse warehouse = Warehouse.create(code, request.name(), request.type());
        warehouse.setAddressLine1(request.addressLine1());
        warehouse.setCity(request.city());
        warehouse.setCountryCode(request.countryCode() != null ? request.countryCode().trim().toUpperCase() : null);

        return toResponse(warehouseRepository.save(warehouse));
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouseById(UUID id) {
        return toResponse(findActiveOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> getWarehouses() {
        return warehouseRepository.findAllActive().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public WarehouseResponse updateWarehouse(UUID id, UpdateWarehouseRequest request) {
        Warehouse warehouse = findActiveOrThrow(id);

        if (request.name() != null) {
            warehouse.setName(request.name().trim());
        }
        if (request.type() != null) {
            warehouse.setType(request.type());
        }
        if (request.addressLine1() != null) {
            warehouse.setAddressLine1(request.addressLine1());
        }
        if (request.city() != null) {
            warehouse.setCity(request.city());
        }
        if (request.countryCode() != null) {
            warehouse.setCountryCode(request.countryCode().trim().toUpperCase());
        }
        if (request.active() != null) {
            warehouse.setActive(request.active());
        }

        return toResponse(warehouseRepository.save(warehouse));
    }

    private Warehouse findActiveOrThrow(UUID id) {
        return warehouseRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", id));
    }

    private WarehouseResponse toResponse(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getType(),
                warehouse.getAddressLine1(),
                warehouse.getCity(),
                warehouse.getCountryCode(),
                warehouse.isActive()
        );
    }
}

