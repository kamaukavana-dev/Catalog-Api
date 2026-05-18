package com.catalog.media.product.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InitiateUploadRequest(
        @NotBlank String contentType,
        @Size(max = 300) String altText
) {
}

