package com.catalog.attribute.api.dto.response;

import java.util.UUID;

public record AttributeValueResponse(
        UUID id,
        UUID attributeTypeId,
        String value,
        String displayValue,
        int displayOrder,
        String hexCode
) {
}

