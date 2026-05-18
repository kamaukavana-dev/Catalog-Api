package com.catalog.product.event;

import java.util.UUID;

public record ProductMutatedEvent(
        UUID id,
        String slug,
        String oldSlug,
        boolean visibilityChanged
) {
}

