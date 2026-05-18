package com.catalog.media.product.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UploadSessionResponse(
        UUID imageId,
        String uploadUrl,
        Instant expiresAt,
        String storageKey
) {
}

