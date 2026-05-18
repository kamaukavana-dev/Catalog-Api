package com.catalog.category.api.dto.response;

import java.util.UUID;

public record CategorySummaryResponse(
    UUID id,
    String name,
    String slug,
    int depth
) {}

