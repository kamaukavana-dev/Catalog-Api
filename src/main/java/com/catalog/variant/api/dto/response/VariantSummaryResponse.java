package com.catalog.variant.api.dto.response;

import com.catalog.variant.domain.VariantStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record VariantSummaryResponse(
        UUID id,
        String internalSku,
        String merchantSku,
        VariantStatus status,
        BigDecimal effectivePrice,
        boolean saleActive,
        List<VariantResponse.AttributeValueDetail> attributes
) {
}

