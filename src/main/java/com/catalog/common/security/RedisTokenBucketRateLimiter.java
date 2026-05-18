package com.catalog.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Redis-backed token bucket limiter for multi-node deployments.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenBucketRateLimiter {

    private static final DefaultRedisScript<List> TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>();

    static {
        TOKEN_BUCKET_SCRIPT.setResultType(List.class);
        TOKEN_BUCKET_SCRIPT.setScriptText("""
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_per_sec = tonumber(ARGV[2])
            local now_ms = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            local ttl_ms = tonumber(ARGV[5])

            local last_refill = tonumber(redis.call('HGET', key, 'ts'))
            local tokens = tonumber(redis.call('HGET', key, 'tokens'))

            if last_refill == nil then last_refill = now_ms end
            if tokens == nil then tokens = capacity end

            local delta_ms = math.max(0, now_ms - last_refill)
            local refill = (delta_ms / 1000.0) * refill_per_sec
            tokens = math.min(capacity, tokens + refill)

            local allowed = 0
            if tokens >= requested then
                tokens = tokens - requested
                allowed = 1
            end

            redis.call('HSET', key, 'tokens', tokens, 'ts', now_ms)
            redis.call('PEXPIRE', key, ttl_ms)

            return { allowed, tokens }
        """);
    }

    private final RedisTemplate<String, String> redisTemplate;

    public boolean tryConsume(String key, int capacity, Duration refillPeriod) {
        long nowMs = System.currentTimeMillis();
        double refillPerSec = capacity / Math.max(1.0, refillPeriod.toSeconds());
        long ttlMs = Math.max(refillPeriod.toMillis() * 2, 60000);

        List result = redisTemplate.execute(
                TOKEN_BUCKET_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(capacity),
                String.valueOf(refillPerSec),
                String.valueOf(nowMs),
                "1",
                String.valueOf(ttlMs)
        );

        if (result == null || result.isEmpty()) {
            log.warn("Redis rate limit script returned empty response for key={}", key);
            return false;
        }

        Object allowed = result.get(0);
        return allowed instanceof Number && ((Number) allowed).longValue() == 1L;
    }
}

