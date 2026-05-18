package com.catalog.attribute.api.dto.response;

import com.catalog.attribute.domain.AttributeDataType;

import java.util.UUID;

public record AttributeTypeResponse(
        UUID id,
        String name,
        String displayName,
        AttributeDataType dataType,
        String unit,
        boolean filterable,
        int displayOrder
) {
}

