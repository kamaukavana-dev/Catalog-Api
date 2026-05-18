package com.catalog.media.product.api.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateImageRequest(
        @Size(max = 300) String altText,
        Integer sortOrder
) {
}

