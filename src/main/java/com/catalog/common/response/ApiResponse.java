package com.catalog.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Generic success envelope for all API responses.
 *
 * <p>Modeled as a Java 21 record: the components are immutable and serialized by
 * Jackson under their component names ({@code success}, {@code message}, {@code data},
 * {@code timestamp}), matching the previous Lombok-backed class byte-for-byte on the
 * wire. {@code @JsonInclude(NON_NULL)} keeps {@code message} omitted when absent.
 * The server stamps {@code timestamp} via the static factories, never the caller.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data, Instant.now());
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, Instant.now());
    }
}
