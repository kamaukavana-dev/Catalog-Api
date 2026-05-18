package com.catalog.category.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
    UUID id,
    String name,
    String slug,
    String description,
    UUID parentId,
    int depth,
    int displayOrder,
    boolean active,
    String imageUrl,
    String metaTitle,
    String metaDescription,
    Instant createdAt,
    Instant updatedAt
) {}

