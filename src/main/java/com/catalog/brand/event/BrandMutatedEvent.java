package com.catalog.brand.event;

import java.util.UUID;

public record BrandMutatedEvent(
        UUID id,
        String slug,
        String oldSlug
) {
}

