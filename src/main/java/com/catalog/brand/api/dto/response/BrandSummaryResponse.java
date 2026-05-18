package com.catalog.brand.api.dto.response;

import java.util.UUID;

public record BrandSummaryResponse(
    UUID id,
    String name,
    String slug,
    String logoUrl
) {}

