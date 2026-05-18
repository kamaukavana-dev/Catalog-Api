package com.catalog.inventory.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record BulkImportJobResponse(
    UUID id,
    UUID importSessionId,
    String status,
    Integer totalRows,
    int processedRows,
    int failedRows,
    String errorSummary,
    Instant createdAt,
    Instant completedAt
) {}

