package com.catalog.variant.event;

import java.util.UUID;

public record VariantMutatedEvent(
        UUID id,
        UUID productId
) {
}

