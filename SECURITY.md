# Security

This document outlines the security mechanisms implemented in the Catalog API.

## 1. Input Validation

All incoming DTOs and request parameters are validated using the **Bean Validation API** (`jakarta.validation`). Custom validation rules are applied where necessary. This is the first line of defense against invalid or malicious data.

**Evidence**:
- `spring-boot-starter-validation` dependency in `pom.xml`.
- `@Valid` and validation annotations (`@NotBlank`, `@Size`, etc.) on DTOs and controller method parameters.

## 2. Rate Limiting

The API implements rate limiting to protect against denial-of-service (DoS) attacks and resource abuse.

- **Strategy**: A token bucket algorithm is used.
- **Implementation**: `RateLimitingFilter` applies per-IP limits with Redis-backed buckets and a Caffeine in-memory fallback.

**Evidence**:
- `com.bucket4j:bucket4j-core` dependency.
- `com.catalog.common.security.RateLimitingFilter`.
- `com.catalog.common.security.RedisTokenBucketRateLimiter`.

## 3. Exception Handling

A global exception handler (`GlobalExceptionHandler`) intercepts unhandled exceptions and translates them into standardized, user-friendly JSON error responses. This prevents stack traces and internal application details from being leaked to the client.

**Evidence**:
- `com.catalog.common.exception.GlobalExceptionHandler`.
- Custom exceptions like `InvalidInputException`.

## 4. File Uploads

File uploads (bulk CSV imports and product images) are validated for size and content type.

**Evidence**:
- `ProductBulkUpdateService#validateFile` and `BulkInventoryService#validateFile`.
- `ProductImageService#initiateUpload` and `ProductImageService#confirmUpload`.
- `GlobalExceptionHandler#handleMaxSizeException`.

## 5. Authentication and Authorization

The codebase includes a minimal API key guard for mutation endpoints.

- **Guard**: `ApiKeyAuthFilter` enforces `X-Api-Key` for `/api/**` non-GET methods when enabled.
- **Configuration**: `catalog.security.require-api-key` and `catalog.security.api-keys`.

**Evidence**:
- `com.catalog.common.security.ApiKeyAuthFilter`.

## 6. Secret Management

The codebase relies on environment variables for secrets and credentials. No inline secrets are committed.

**Evidence**:
- `application.yml` uses `${...}` placeholders for DB and storage credentials.
- `.env.example` provides placeholders for local development.
