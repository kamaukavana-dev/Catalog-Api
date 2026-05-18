package com.catalog.media.product.api.dto.response;

import com.catalog.media.domain.ImageStatus;

import java.time.Instant;
import java.util.UUID;

public record ProductImageResponse(
        UUID id,
        String url,
        String altText,
        boolean primary,
        int sortOrder,
        ImageStatus status,
        String contentType,
        Long fileSizeBytes,
        Integer widthPx,
        Integer heightPx,
        Instant createdAt
) {
}

