package com.catalog.warehouse.api.dto.request;

import com.catalog.warehouse.domain.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWarehouseRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull WarehouseType type,
        @Size(max = 300) String addressLine1,
        @Size(max = 100) String city,
        @Size(max = 2) String countryCode
) {
}

