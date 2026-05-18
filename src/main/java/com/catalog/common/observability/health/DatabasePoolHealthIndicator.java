package com.catalog.common.observability.health;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Monitors HikariCP connection pool saturation.
 *
 * HikariCP raw metrics (active/idle/pending) are exposed automatically
 * via Micrometer at /actuator/prometheus.
 *
 * This health indicator converts pool saturation into a health status
 * for the /actuator/health endpoint and Kubernetes readiness probes.
 *
 * Saturation thresholds:
 * > 80%: DEGRADED — warn operations team
 * > 95%: DOWN — stop accepting traffic, new requests will time out
 */
@Slf4j
@Component("db-pool")
@RequiredArgsConstructor
public class DatabasePoolHealthIndicator implements HealthIndicator {

    private static final double DEGRADED_THRESHOLD = 0.80;
    private static final double DOWN_THRESHOLD = 0.95;

    private final DataSource dataSource;

    @Override
    public Health health() {
        if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
            return Health.unknown()
                    .withDetail("reason", "DataSource is not HikariCP")
                    .build();
        }

        HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
        if (pool == null) {
            return Health.unknown()
                    .withDetail("reason", "HikariCP pool not yet initialized")
                    .build();
        }

        int active = pool.getActiveConnections();
        int idle = pool.getIdleConnections();
        int total = pool.getTotalConnections();
        int waiting = pool.getThreadsAwaitingConnection();
        int maxPoolSize = hikariDataSource.getMaximumPoolSize();

        double utilization = maxPoolSize > 0 ? (double) active / maxPoolSize : 0;

        Health.Builder builder = Health.up()
                .withDetail("active_connections", active)
                .withDetail("idle_connections", idle)
                .withDetail("total_connections", total)
                .withDetail("threads_waiting", waiting)
                .withDetail("max_pool_size", maxPoolSize)
                .withDetail("utilization_pct", String.format("%.1f%%", utilization * 100));

        if (utilization >= DOWN_THRESHOLD || waiting > 10) {
            log.error("DB pool CRITICAL: utilization={} waiting={}",
                    String.format("%.1f%%", utilization * 100), waiting);
            return builder.status(Health.down().build().getStatus())
                    .withDetail("reason", "Connection pool critically saturated")
                    .build();
        }

        if (utilization >= DEGRADED_THRESHOLD) {
            log.warn("DB pool DEGRADED: utilization={}", String.format("%.1f%%", utilization * 100));
            return builder.status(CatalogHealthStatus.DEGRADED)
                    .withDetail("reason", "Connection pool approaching saturation")
                    .build();
        }

        return builder.build();
    }
}
