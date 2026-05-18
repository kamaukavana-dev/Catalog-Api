package com.catalog.variant.api.dto.request;

import com.catalog.variant.domain.VariantStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateVariantStatusRequest(

        @NotNull(message = "Target status is required")
        VariantStatus targetStatus
) {
}

