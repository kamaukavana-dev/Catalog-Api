package com.catalog.variant.api.dto.request;

import com.catalog.variant.domain.TaxClass;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UpdateVariantRequest(

        @Size(max = 200, message = "Merchant SKU must not exceed 200 characters")
        String merchantSku,

        @DecimalMin(value = "0.0001", message = "Base price must be greater than zero")
        @Digits(integer = 15, fraction = 4)
        BigDecimal basePrice,

        @DecimalMin(value = "0.0001", message = "Sale price must be greater than zero")
        @Digits(integer = 15, fraction = 4)
        BigDecimal salePrice,

        Instant saleStartAt,

        Instant saleEndAt,

        @Digits(integer = 15, fraction = 4)
        BigDecimal costPrice,

        TaxClass taxClass,

        Set<UUID> attributeValueIds,

        @Min(0)
        Integer weightGrams,

        @Min(0)
        Integer lengthMm,

        @Min(0)
        Integer widthMm,

        @Min(0)
        Integer heightMm
) {
}

