package com.catalog.brand.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record BrandResponse(
    UUID id,
    String name,
    String slug,
    String description,
    String logoUrl,
    String websiteUrl,
    String countryOfOrigin,
    Integer foundedYear,
    boolean active,
    boolean featured,
    Instant createdAt,
    Instant updatedAt
) {}

