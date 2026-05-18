package com.catalog.attribute.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAttributeValueRequest(

        @NotBlank(message = "Value is required")
        @Size(min = 1, max = 200)
        String value,

        @NotBlank(message = "Display value is required")
        @Size(min = 1, max = 200)
        String displayValue,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Hex code must be a valid color: #RRGGBB")
        String hexCode,

        Integer displayOrder
) {
}

