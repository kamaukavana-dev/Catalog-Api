package com.catalog.common.observability.health;

import com.catalog.media.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

@Slf4j
@Component("catalog-storage")
@RequiredArgsConstructor
public class StorageHealthIndicator implements HealthIndicator {

    private final S3Client s3Client;
    private final StorageConfig storageConfig;

    @Override
    public Health health() {
        try {
            long start = System.currentTimeMillis();
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(storageConfig.getBucket())
                    .build());
            long latencyMs = System.currentTimeMillis() - start;

            return Health.up()
                    .withDetail("bucket", storageConfig.getBucket())
                    .withDetail("latency_ms", latencyMs)
                    .build();

        } catch (NoSuchBucketException e) {
            // Bucket doesn't exist — critical configuration error
            return Health.down()
                    .withDetail("reason", "Configured bucket does not exist: " + storageConfig.getBucket())
                    .withDetail("action", "Create the bucket or fix STORAGE_BUCKET configuration")
                    .build();

        } catch (Exception e) {
            log.warn("Storage health check failed: {}", e.getMessage());
            // DEGRADED not DOWN: image uploads will fail, but catalog reads work
            return Health.status(CatalogHealthStatus.DEGRADED)
                    .withDetail("reason", "Object storage unreachable: " + e.getMessage())
                    .build();
        }
    }
}

