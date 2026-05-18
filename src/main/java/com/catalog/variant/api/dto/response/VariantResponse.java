package com.catalog.variant.api.dto.response;

import com.catalog.variant.domain.TaxClass;
import com.catalog.variant.domain.VariantStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VariantResponse(
        UUID id,
        UUID productId,
        String internalSku,
        String merchantSku,
        VariantStatus status,
        BigDecimal basePrice,
        BigDecimal salePrice,
        BigDecimal effectivePrice,
        boolean saleActive,
        Instant saleStartAt,
        Instant saleEndAt,
        BigDecimal costPrice,
        TaxClass taxClass,
        Integer weightGrams,
        Integer lengthMm,
        Integer widthMm,
        Integer heightMm,
        List<AttributeValueDetail> attributes,
        Instant createdAt,
        Instant updatedAt
) {
    public record AttributeValueDetail(
            UUID attributeTypeId,
            String attributeTypeName,
            String attributeTypeDisplayName,
            UUID attributeValueId,
            String value,
            String displayValue,
            String hexCode
    ) {
    }
}

