package com.catalog.common.security;

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

    public RateLimitingFilter(RedisTokenBucketRateLimiter redisRateLimiter) {
        this.redisRateLimiter = redisRateLimiter;
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
        response.getWriter().write("""
            {"status":429,"error":"Too Many Requests",
             "message":"Rate limit exceeded. Retry after 60 seconds."}
            """);
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
        // Respect proxy headers when behind a load balancer
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
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
