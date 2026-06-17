package com.catalog.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final Instant timestamp;

    // Field-level validation errors: [{"field": "...", "message": "..."}]
    private final List<ValidationError> validationErrors;

    public record ValidationError(String field, String message) {}
}
