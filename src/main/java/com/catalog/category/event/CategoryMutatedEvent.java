package com.catalog.category.event;

import java.util.UUID;

public record CategoryMutatedEvent(
        UUID id,
        String slug,
        String oldSlug
) {
}

