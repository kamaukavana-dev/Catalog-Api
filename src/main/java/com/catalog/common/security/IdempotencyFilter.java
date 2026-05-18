package com.catalog.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/**
 * Idempotency key support for mutation endpoints.
 *
 * Client sends: X-Idempotency-Key: <uuid>
 * First request: process and cache response for 24 hours
 * Duplicate request: return cached response immediately
 *
 * Prevents duplicate product creation, double charges, duplicate reservations
 * when clients retry on network timeout.
 *
 * Applies ONLY to POST requests (mutations that create resources).
 * PUT/PATCH operations are inherently idempotent by REST semantics.
 *
 * Note: Idempotency keys are per-client (use Auth header as namespace in secured endpoints).
 * Currently namespace is global — acceptable before Spring Security phase.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final String CACHE_PREFIX = "idempotency:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        // Only applies to POST requests with an idempotency header
        if (!"POST".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String requestHash = sha256(wrappedRequest.getCachedBody());

        String cacheKey = buildCacheKey(request, idempotencyKey);

        // Check if we've seen this key before
        IdempotencyRecord cachedResponse = getCachedResponse(cacheKey);
        if (cachedResponse != null) {
            if (!Objects.equals(cachedResponse.requestHash(), requestHash)) {
                respondConflict(response);
                return;
            }
            log.debug("Idempotency hit: key={}", idempotencyKey);
            response.setStatus(cachedResponse.status());
            if (cachedResponse.contentType() != null) {
                response.setContentType(cachedResponse.contentType());
            }
            if (cachedResponse.location() != null) {
                response.setHeader("Location", cachedResponse.location());
            }
            response.setHeader("X-Idempotency-Replayed", "true");
            if (cachedResponse.body() != null && !cachedResponse.body().isBlank()) {
                response.getWriter().write(cachedResponse.body());
            }
            return;
        }

        // Execute the request, capturing the response
        ContentCachingResponseWrapper responseWrapper =
                new ContentCachingResponseWrapper(response);
        chain.doFilter(wrappedRequest, responseWrapper);

        // Cache successful responses only (2xx)
        if (isSuccessful(responseWrapper.getStatus())) {
            String body = new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            if (body.getBytes(StandardCharsets.UTF_8).length <= MAX_RESPONSE_BYTES) {
                IdempotencyRecord record = new IdempotencyRecord(
                        responseWrapper.getStatus(),
                        responseWrapper.getContentType(),
                        responseWrapper.getHeader("Location"),
                        body,
                        requestHash
                );
                cacheResponse(cacheKey, record);
            } else {
                log.warn("Idempotency response too large to cache: key={} bytes={}",
                        idempotencyKey, body.length());
            }
        }

        responseWrapper.copyBodyToResponse();
    }

    private IdempotencyRecord getCachedResponse(String key) {
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                return null;
            }
            return objectMapper.readValue(cached, IdempotencyRecord.class);
        } catch (Exception e) {
            log.warn("Idempotency cache read failed: {}", e.getMessage());
            return null; // Fail open: process the request
        }
    }

    private void cacheResponse(String key, IdempotencyRecord record) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(record), CACHE_TTL);
        } catch (Exception e) {
            log.warn("Idempotency cache write failed: {}", e.getMessage());
            // Non-fatal: the operation succeeded, just won't be idempotent on retry
        }
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

    private void respondConflict(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType("application/json");
        response.getWriter().write("""
            {"status":409,"error":"Idempotency Conflict",
             "message":"Idempotency key was reused with a different request payload."}
            """);
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

    private record IdempotencyRecord(int status, String contentType, String location, String body, String requestHash) {}
}
