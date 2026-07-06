package com.catalog.common.security;

import com.catalog.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/**
 * Idempotency-key support for mutation endpoints.
 *
 * <p>Client sends {@code X-Idempotency-Key: <token>}. The first request for a
 * (method, path, identity, key) tuple executes and its 2xx response is cached for
 * 24 hours; a later retry with the same key and payload replays that response.
 *
 * <h2>Atomic claim (no check-then-write race)</h2>
 * The key is claimed with a single {@code SET key <in-progress> NX PX <ttl>}
 * ({@link org.springframework.data.redis.core.ValueOperations#setIfAbsent}). Exactly
 * one concurrent request can win the claim; the losers observe the existing key and are
 * rejected (409) or replay the completed result. There is no window in which two requests
 * both believe they are first. The earlier implementation did an independent GET followed
 * by a later SET, so two concurrent retries could both miss the GET and both execute the
 * mutation — the race this class now closes.
 *
 * <h2>Redis-unavailable policy (per endpoint, explicit)</h2>
 * When the idempotency store cannot be reached the guarantee cannot be honoured, so the
 * behaviour is chosen by how damaging a duplicate side effect would be:
 * <ul>
 *   <li><b>Fail closed (503)</b> for stock/money-moving endpoints — everything under
 *       {@code /api/v1/inventory} (stock create/adjust, reservation create/complete/cancel,
 *       transfers), {@code /api/v1/transfers}, and bulk apply jobs
 *       ({@code /api/v1/inventory/bulk-imports}, {@code /api/v1/products/bulk-update}).
 *       A duplicate here oversells stock, double-moves inventory, or re-applies a whole
 *       file — none of which a DB constraint would catch — so we refuse rather than risk it.</li>
 *   <li><b>Fail open</b> for catalog CRUD (brands, categories, warehouses, variants,
 *       attributes, products). These creates are guarded by DB unique constraints
 *       (slug / internal SKU / warehouse code / attribute code) that reject a true
 *       duplicate on their own, and id-addressed PUT/PATCH/DELETE are naturally
 *       idempotent, so proceeding without the Redis guard is safe.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final String CACHE_PREFIX = "idempotency:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    // A claim placeholder lives only until the first request finishes. If that request
    // dies without releasing the claim, the marker expires and retries can proceed.
    private static final Duration IN_PROGRESS_TTL = Duration.ofSeconds(30);
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        if (!isMutationRequest(request) || !isApiPath(request)) {
            chain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            respondMissingKey(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String requestHash = sha256(wrappedRequest.getCachedBody());
        String cacheKey = buildCacheKey(request, idempotencyKey);

        // Atomically claim the key. TRUE => we are the first and must execute; FALSE => a
        // record already exists (in-progress or completed); an exception => Redis is down.
        Boolean claimed;
        try {
            String inProgress = objectMapper.writeValueAsString(IdempotencyRecord.inProgress(requestHash));
            claimed = redisTemplate.opsForValue().setIfAbsent(cacheKey, inProgress, IN_PROGRESS_TTL);
        } catch (Exception e) {
            handleStoreUnavailable(request, response, chain, wrappedRequest, e);
            return;
        }

        if (!Boolean.TRUE.equals(claimed)) {
            replayOrReject(request, response, cacheKey, requestHash);
            return;
        }

        // We own the claim: execute the request and record (or release) the outcome.
        boolean released = false;
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(wrappedRequest, responseWrapper);

            if (isSuccessful(responseWrapper.getStatus())) {
                String body = new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
                if (body.getBytes(StandardCharsets.UTF_8).length <= MAX_RESPONSE_BYTES) {
                    IdempotencyRecord record = IdempotencyRecord.completed(
                            responseWrapper.getStatus(),
                            responseWrapper.getContentType(),
                            responseWrapper.getHeader("Location"),
                            body,
                            requestHash);
                    storeCompleted(cacheKey, record);
                    released = true;
                } else {
                    log.warn("Idempotency response too large to cache: key={} bytes={}",
                            idempotencyKey, body.length());
                }
            }
        } finally {
            // A non-2xx (or oversized) outcome must not leave the claim marker behind, or a
            // legitimate retry would be blocked until IN_PROGRESS_TTL. Release it so the
            // client can try again.
            if (!released) {
                safeDelete(cacheKey);
            }
            responseWrapper.copyBodyToResponse();
        }
    }

    private void replayOrReject(HttpServletRequest request, HttpServletResponse response,
                                String cacheKey, String requestHash) throws IOException {
        IdempotencyRecord existing;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            existing = cached == null ? null : objectMapper.readValue(cached, IdempotencyRecord.class);
        } catch (Exception e) {
            log.warn("Idempotency lookup of an existing claim failed: {}", e.getMessage());
            respondConflict(request, response,
                    "A request with this idempotency key is already being processed.");
            return;
        }

        if (existing == null) {
            // The claim expired or was released between our failed claim and this read.
            respondConflict(request, response,
                    "A request with this idempotency key is already being processed.");
            return;
        }
        if (!Objects.equals(existing.requestHash(), requestHash)) {
            respondConflict(request, response,
                    "Idempotency key was reused with a different request payload.");
            return;
        }
        if (!existing.completed()) {
            respondConflict(request, response,
                    "A request with this idempotency key is already being processed.");
            return;
        }

        log.debug("Idempotency replay for cached response");
        response.setStatus(existing.status());
        if (existing.contentType() != null) {
            response.setContentType(existing.contentType());
        }
        if (existing.location() != null) {
            response.setHeader("Location", existing.location());
        }
        response.setHeader("X-Idempotency-Replayed", "true");
        if (existing.body() != null && !existing.body().isBlank()) {
            response.getWriter().write(existing.body());
        }
    }

    private void handleStoreUnavailable(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain, CachedBodyHttpServletRequest wrappedRequest,
                                        Exception cause) throws ServletException, IOException {
        if (isFailClosed(request.getRequestURI())) {
            log.error("Idempotency store unavailable; failing closed for state-critical path {}: {}",
                    request.getRequestURI(), cause.getMessage());
            writeErrorResponse(request, response,
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "Service Unavailable",
                    "Idempotency store is unavailable and this operation cannot be safely retried. "
                            + "Please retry shortly.");
            return;
        }
        log.warn("Idempotency store unavailable; failing open for idempotent-by-constraint path {}: {}",
                request.getRequestURI(), cause.getMessage());
        chain.doFilter(wrappedRequest, response);
    }

    /**
     * State-critical paths whose duplication is not caught by a DB constraint and would
     * move stock or re-apply a bulk job. Kept in sync with the class-level policy doc.
     */
    private boolean isFailClosed(String path) {
        if (path == null) {
            return true; // unknown path: be conservative
        }
        return path.startsWith("/api/v1/inventory")
                || path.startsWith("/api/v1/transfers")
                || path.contains("/bulk-update");
    }

    private void storeCompleted(String cacheKey, IdempotencyRecord record) {
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(record), CACHE_TTL);
        } catch (Exception e) {
            log.warn("Idempotency completion write failed: {}", e.getMessage());
        }
    }

    private void safeDelete(String cacheKey) {
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("Idempotency claim release failed: {}", e.getMessage());
        }
    }

    private boolean isApiPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/");
    }

    private boolean isMutationRequest(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private boolean isSuccessful(int status) {
        return status >= 200 && status < 300;
    }

    private String buildCacheKey(HttpServletRequest request, String idempotencyKey) {
        String authHeader = request.getHeader("Authorization");
        String apiKeyHeader = request.getHeader("X-Api-Key");
        String identity = authHeader != null ? authHeader : apiKeyHeader;
        String authHash = identity == null ? "anonymous" : sha256(identity);
        return CACHE_PREFIX + request.getMethod() + ":" + request.getRequestURI() + ":" + authHash + ":" + idempotencyKey;
    }

    private void respondMissingKey(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writeErrorResponse(request, response,
                HttpServletResponse.SC_BAD_REQUEST,
                "Bad Request",
                "Missing required header: " + IDEMPOTENCY_HEADER + ".");
    }

    private void respondConflict(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        writeErrorResponse(request, response,
                HttpServletResponse.SC_CONFLICT,
                "Idempotency Conflict",
                message);
    }

    private void writeErrorResponse(HttpServletRequest request,
                                    HttpServletResponse response,
                                    int status,
                                    String error,
                                    String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        ErrorResponse body = ErrorResponse.builder()
                .status(status)
                .error(error)
                .message(message)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record IdempotencyRecord(int status, String contentType, String location, String body,
                                     String requestHash, boolean completed) {
        static IdempotencyRecord inProgress(String requestHash) {
            return new IdempotencyRecord(0, null, null, null, requestHash, false);
        }

        static IdempotencyRecord completed(int status, String contentType, String location,
                                           String body, String requestHash) {
            return new IdempotencyRecord(status, contentType, location, body, requestHash, true);
        }
    }
}
