package com.catalog.inventory.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record InventoryJournalResponse(
    UUID id,
    String operationType,
    int quantityBefore,
    int quantityAfter,
    int quantityDelta,
    int reservedBefore,
    int reservedAfter,
    int reservedDelta,
    String referenceType,
    UUID referenceId,
    String actorType,
    String reason,
    Instant createdAt
) {}

