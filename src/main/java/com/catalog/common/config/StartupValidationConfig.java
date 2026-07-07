package com.catalog.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fail-fast startup validation.
 *
 * Validates all required configuration exists before the application
 * completes startup. Prevents the "it started but everything fails"
 * class of production incidents.
 *
 * A missing STORAGE_BUCKET env var should kill the app at boot,
 * NOT at the first image upload 3 hours after deployment.
 */
@Slf4j
@Component
public class StartupValidationConfig implements InitializingBean {

    private final String storageBucket;
    private final String storageEndpoint;
    private final String storageAccessKey;
    private final String storageSecretKey;
    private final String redisHost;
    private final DataSource dataSource;

    public StartupValidationConfig(
            @Value("${catalog.storage.bucket:}") String storageBucket,
            @Value("${catalog.storage.endpoint:}") String storageEndpoint,
            @Value("${catalog.storage.access-key:}") String storageAccessKey,
            @Value("${catalog.storage.secret-key:}") String storageSecretKey,
            @Value("${spring.data.redis.host:}") String redisHost,
            DataSource dataSource) {
        this.storageBucket = storageBucket;
        this.storageEndpoint = storageEndpoint;
        this.storageAccessKey = storageAccessKey;
        this.storageSecretKey = storageSecretKey;
        this.redisHost = redisHost;
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> violations = new ArrayList<>();

        assertRequired("catalog.storage.bucket (STORAGE_BUCKET)", storageBucket, violations);
        assertRequired("catalog.storage.endpoint (STORAGE_ENDPOINT)", storageEndpoint, violations);
        assertRequired("catalog.storage.access-key (STORAGE_ACCESS_KEY)", storageAccessKey, violations);
        assertRequired("catalog.storage.secret-key (STORAGE_SECRET_KEY)", storageSecretKey, violations);
        assertDatabaseReachable(violations);
        assertRequired("spring.data.redis.host (REDIS_HOST)", redisHost, violations);

        if (!violations.isEmpty()) {
            String message = "APPLICATION STARTUP FAILED: Missing required configuration:\n" +
                             String.join("\n", violations.stream()
                                 .map(v -> "  ✗ " + v).toList());
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.info("Startup validation passed. All required configuration present.");
    }

    private void assertRequired(String name, String value, List<String> violations) {
        if (value == null || value.isBlank()) {
            violations.add(name);
        }
    }

    /**
     * Validate the database is actually reachable at boot, not just that a URL string
     * is present. The datasource URL cannot be read as a plain property here because
     * under Testcontainers it is supplied via a {@code JdbcConnectionDetails} bean and
     * the {@code spring.datasource.url} property is never materialized — reading it would
     * throw an unresolved-placeholder error. Probing the {@link DataSource} bean works in
     * every environment and is strictly stronger: it also catches a wrong host, port,
     * database name, or credentials before the first request hits them.
     */
    private void assertDatabaseReachable(List<String> violations) {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) {
                violations.add("database connectivity (DB_URL / DB_USERNAME / DB_PASSWORD): connection is not valid");
            }
        } catch (SQLException e) {
            violations.add("database connectivity (DB_URL / DB_USERNAME / DB_PASSWORD): " + e.getMessage());
        }
    }
}
