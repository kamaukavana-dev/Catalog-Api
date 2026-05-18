package com.catalog.common.observability.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component("redis")
@RequiredArgsConstructor
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public Health health() {
        try {
            long start = System.currentTimeMillis();
            String pong;
            try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
                pong = connection.ping();
            }
            long latencyMs = System.currentTimeMillis() - start;

            if (!"PONG".equalsIgnoreCase(pong)) {
                return Health.status(CatalogHealthStatus.DEGRADED)
                        .withDetail("reason", "Unexpected ping response: " + pong)
                        .build();
            }

            // Warn if Redis latency is elevated — may indicate memory pressure
            if (latencyMs > 100) {
                return Health.status(CatalogHealthStatus.DEGRADED)
                        .withDetail("latency_ms", latencyMs)
                        .withDetail("reason", "Redis responding but with elevated latency")
                        .build();
            }

            return Health.up()
                    .withDetail("latency_ms", latencyMs)
                    .build();

        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            // DEGRADED not DOWN: Redis failure degrades cache, not the entire API.
            // Core operations (product CRUD, inventory, checkout) still work via DB.
            return Health.status(CatalogHealthStatus.DEGRADED)
                    .withDetail("reason", "Redis unavailable: " + e.getMessage())
                    .build();
        }
    }
}
