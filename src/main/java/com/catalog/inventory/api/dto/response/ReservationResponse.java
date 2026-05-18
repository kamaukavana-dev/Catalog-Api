package com.catalog.inventory.api.dto.response;

import com.catalog.inventory.domain.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID inventoryId,
        UUID referenceId,
        int quantity,
        ReservationStatus status,
        Instant expiresAt,
        Instant releasedAt,
        Instant createdAt
) {
}

