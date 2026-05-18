package com.catalog.warehouse.api.dto.request;

import com.catalog.warehouse.domain.WarehouseType;
import jakarta.validation.constraints.Size;

public record UpdateWarehouseRequest(
        @Size(max = 200) String name,
        WarehouseType type,
        @Size(max = 300) String addressLine1,
        @Size(max = 100) String city,
        @Size(max = 2) String countryCode,
        Boolean active
) {
}

