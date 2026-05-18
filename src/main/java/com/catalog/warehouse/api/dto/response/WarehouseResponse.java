package com.catalog.warehouse.api.dto.response;

import com.catalog.warehouse.domain.WarehouseType;

import java.util.UUID;

public record WarehouseResponse(
        UUID id,
        String code,
        String name,
        WarehouseType type,
        String addressLine1,
        String city,
        String countryCode,
        boolean active
) {
}

