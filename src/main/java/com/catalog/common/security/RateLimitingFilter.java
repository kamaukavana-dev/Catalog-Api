package com.catalog.common.security;

import com.catalog.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.Duration;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Per-IP token bucket rate limiting.
 *
 * Phase 14 implementation: in-process (single instance).
 * Production multi-instance: replace ConcurrentHashMap with Redis-backed Bucket4j.
 * Library: bucket4j-redis or bucket4j-spring-boot-starter with Redis backend.
 *
 * Limits:
 * - Standard endpoints: 100 requests/minute per IP
 * - Write endpoints (POST/PUT/PATCH/DELETE): 20 requests/minute per IP
 * - Bulk import: 5 requests/minute per IP (heavy operations)
 *
 * HTTP 429 Too Many Requests on violation.
 * Retry-After header informs clients when to retry.
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String REDIS_PREFIX = "rate-limit:";

    private final RedisTokenBucketRateLimiter redisRateLimiter;
    private final ObjectMapper objectMapper;

    /**
     * Number of trusted reverse proxies / load balancers in front of this service.
     * The client IP is taken {@code trustedProxyCount} positions from the right end
     * of X-Forwarded-For, because each trusted hop appends the address it saw. The
     * leftmost entries are attacker-controlled and must never be used as the rate-limit
     * identity. Set to 0 to ignore X-Forwarded-For entirely (direct exposure).
     */
    private final int trustedProxyCount;

    // Bucket per client IP — bounded + expired to avoid unbounded memory growth.
    private final Cache<String, Bucket> readBuckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();
    private final Cache<String, Bucket> writeBuckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();
    private final Cache<String, Bucket> bulkBuckets = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public RateLimitingFilter(RedisTokenBucketRateLimiter redisRateLimiter,
                              ObjectMapper objectMapper,
                              @org.springframework.beans.factory.annotation.Value("${catalog.security.trusted-proxy-count:1}") int trustedProxyCount) {
        this.redisRateLimiter = redisRateLimiter;
        this.objectMapper = objectMapper;
        this.trustedProxyCount = Math.max(0, trustedProxyCount);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String clientKey = clientKey(request, clientIp);
        String path = request.getRequestURI();
        String method = request.getMethod();

        Decision decision = tryConsumeRedis(clientKey, path, method);
        boolean allowed = switch (decision) {
            case ALLOWED -> true;
            case DENIED -> false;
            case FALLBACK -> selectBucket(clientKey, path, method).tryConsume(1);
        };

        if (allowed) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit exceeded: ip={} method={} path={}", clientIp, method, path);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        writeErrorResponse(request, response,
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                "Rate limit exceeded. Retry after 60 seconds.");
    }

    private void writeErrorResponse(HttpServletRequest request,
                                    HttpServletResponse response,
                                    int status,
                                    String error,
                                    String message) throws IOException {
        ErrorResponse body = ErrorResponse.builder()
                .status(status)
                .error(error)
                .message(message)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private Bucket selectBucket(String clientKey, String path, String method) {
        if (isBulkPath(path)) {
            return bulkBuckets.get(clientKey, k -> buildBucket(5, Duration.ofMinutes(1)));
        }
        if (isWriteMethod(method)) {
            return writeBuckets.get(clientKey, k -> buildBucket(20, Duration.ofMinutes(1)));
        }
        return readBuckets.get(clientKey, k -> buildBucket(100, Duration.ofMinutes(1)));
    }

    private Bucket buildBucket(int capacity, Duration refillPeriod) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(capacity,
                    Refill.intervally(capacity, refillPeriod)))
                .build();
    }

    private boolean isWriteMethod(String method) {
        return "POST".equals(method) || "PUT".equals(method)
            || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private String extractClientIp(HttpServletRequest request) {
        // Only trust X-Forwarded-For when we know how many proxies sit in front of us.
        // Each trusted hop appends the peer it saw, so the genuine client address is
        // `trustedProxyCount` entries from the right. Taking the leftmost entry (the old
        // behaviour) trusts an attacker-supplied value, letting a client mint a fresh
        // rate-limit bucket per request by rotating the header — defeating rate limiting.
        if (trustedProxyCount > 0) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                int idx = hops.length - trustedProxyCount;
                if (idx >= 0 && idx < hops.length) {
                    String candidate = hops[idx].trim();
                    if (!candidate.isBlank()) {
                        return candidate;
                    }
                }
            }
        }
        return request.getRemoteAddr();
    }

    private Decision tryConsumeRedis(String clientKey, String path, String method) {
        try {
            String key = REDIS_PREFIX + clientKey + ":" + bucketKey(path, method);
            if (isBulkPath(path)) {
                return redisRateLimiter.tryConsume(key, 5, Duration.ofMinutes(1))
                        ? Decision.ALLOWED : Decision.DENIED;
            }
            if (isWriteMethod(method)) {
                return redisRateLimiter.tryConsume(key, 20, Duration.ofMinutes(1))
                        ? Decision.ALLOWED : Decision.DENIED;
            }
            return redisRateLimiter.tryConsume(key, 100, Duration.ofMinutes(1))
                    ? Decision.ALLOWED : Decision.DENIED;
        } catch (Exception ex) {
            log.warn("Redis rate limiting unavailable, falling back to in-memory: {}", ex.getMessage());
            return Decision.FALLBACK;
        }
    }

    private String bucketKey(String path, String method) {
        if (isBulkPath(path)) {
            return "bulk";
        }
        if (isWriteMethod(method)) {
            return "write";
        }
        return "read";
    }

    private boolean isBulkPath(String path) {
        return path.contains("/bulk-imports") || path.contains("/bulk-update");
    }

    private String clientKey(HttpServletRequest request, String clientIp) {
        String apiKey = request.getHeader("X-Api-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return "api:" + sha256(apiKey.trim());
        }
        return "ip:" + clientIp;
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private enum Decision {
        ALLOWED,
        DENIED,
        FALLBACK
    }
}
