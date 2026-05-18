package com.catalog.common.observability.health;

import org.springframework.boot.actuate.health.Status;

/**
 * Custom health statuses beyond Spring Boot defaults.
 *
 * DEGRADED: subsystem is impaired but not fully down.
 * System continues operating with reduced capability.
 * HTTP 200 is returned — load balancer should keep routing traffic.
 *
 * Configuration in application.yml:
 *   management.endpoint.health.status.order: DOWN, OUT_OF_SERVICE, DEGRADED, UNKNOWN, UP
 *   management.endpoint.health.status.http-mapping.DEGRADED: 200
 */
public final class CatalogHealthStatus {

    private CatalogHealthStatus() {}

    /**
     * Operational but impaired. System continues with reduced capability.
     * Example: Redis is down — reads fall through to DB, latency is higher
     * but the system is functional.
     */
    public static final Status DEGRADED = new Status("DEGRADED");
}

