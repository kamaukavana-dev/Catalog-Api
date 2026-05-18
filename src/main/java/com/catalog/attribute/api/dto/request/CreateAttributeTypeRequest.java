package com.catalog.attribute.api.dto.request;

import com.catalog.attribute.domain.AttributeDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAttributeTypeRequest(

        @NotBlank(message = "Attribute type name is required")
        @Size(min = 1, max = 100)
        String name,

        @NotBlank(message = "Display name is required")
        @Size(min = 1, max = 100)
        String displayName,

        @NotNull(message = "Data type is required")
        AttributeDataType dataType,

        @Size(max = 50)
        String unit,

        Boolean filterable,

        Integer displayOrder
) {
}

