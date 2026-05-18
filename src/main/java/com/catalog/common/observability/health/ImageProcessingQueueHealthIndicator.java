package com.catalog.common.observability.health;

import com.catalog.media.domain.ImageStatus;
import com.catalog.product.infrastructure.ProductImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component("image-queue")
public class ImageProcessingQueueHealthIndicator implements HealthIndicator {

    private final ProductImageRepository imageRepository;
    private final int staleProcessingThresholdMinutes;
    private final int maxFailedCount;

    public ImageProcessingQueueHealthIndicator(
            ProductImageRepository imageRepository,
            @Value("${catalog.health.image.stale-processing-threshold-minutes:30}") int staleProcessingThresholdMinutes,
            @Value("${catalog.health.image.max-failed-count:50}") int maxFailedCount) {
        this.imageRepository = imageRepository;
        this.staleProcessingThresholdMinutes = staleProcessingThresholdMinutes;
        this.maxFailedCount = maxFailedCount;
    }

    @Override
    public Health health() {
        try {
            // Count images stuck in PROCESSING (async job hung/crashed)
            Instant staleThreshold = Instant.now()
                    .minus(staleProcessingThresholdMinutes, ChronoUnit.MINUTES);
            long stuckProcessing = imageRepository.countStuckInProcessing(staleThreshold);

            // Count failed images accumulated
            long failedCount = imageRepository.countByStatus(ImageStatus.FAILED);

            if (stuckProcessing > 0) {
                return Health.status(CatalogHealthStatus.DEGRADED)
                        .withDetail("stuck_processing", stuckProcessing)
                        .withDetail("failed_images", failedCount)
                        .withDetail("reason", "Images stuck in PROCESSING. " +
                                "Async processor may be unhealthy.")
                        .build();
            }

            if (failedCount > maxFailedCount) {
                return Health.status(CatalogHealthStatus.DEGRADED)
                        .withDetail("failed_images", failedCount)
                        .withDetail("threshold", maxFailedCount)
                        .withDetail("reason", "High number of failed images. " +
                                "Storage or processing errors accumulating.")
                        .build();
            }

            return Health.up()
                    .withDetail("failed_images", failedCount)
                    .withDetail("stuck_processing", stuckProcessing)
                    .build();

        } catch (Exception e) {
            return Health.status(CatalogHealthStatus.DEGRADED)
                    .withDetail("reason", "Could not query image status: " + e.getMessage())
                    .build();
        }
    }
}
