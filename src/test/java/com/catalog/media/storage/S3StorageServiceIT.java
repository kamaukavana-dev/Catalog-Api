package com.catalog.media.storage;

import com.catalog.common.BaseIntegrationTest;
import com.catalog.common.exception.StorageUnavailableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class S3StorageServiceIT extends BaseIntegrationTest {

    private static final WireMockServer wireMock =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        if (!wireMock.isRunning()) {
            wireMock.start();
        }

        registry.add("catalog.storage.bucket", () -> "test-bucket");
        registry.add("catalog.storage.endpoint", wireMock::baseUrl);
        registry.add("catalog.storage.base-url", () -> wireMock.baseUrl() + "/test-bucket");
        registry.add("catalog.storage.region", () -> "us-east-1");
        registry.add("catalog.storage.access-key", () -> "test-access");
        registry.add("catalog.storage.secret-key", () -> "test-secret");

        registry.add("resilience4j.circuitbreaker.instances.object-storage.sliding-window-size", () -> "2");
        registry.add("resilience4j.circuitbreaker.instances.object-storage.minimum-number-of-calls", () -> "2");
        registry.add("resilience4j.circuitbreaker.instances.object-storage.failure-rate-threshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.instances.object-storage.wait-duration-in-open-state", () -> "200ms");
        registry.add("resilience4j.circuitbreaker.instances.object-storage.permitted-number-of-calls-in-half-open-state", () -> "1");

        registry.add("resilience4j.retry.instances.object-storage.max-attempts", () -> "1");
        registry.add("resilience4j.retry.instances.object-storage.wait-duration", () -> "0ms");
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock.isRunning()) {
            wireMock.stop();
        }
    }

    @Autowired private S3StorageService s3StorageService;
    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("object-storage");
        cb.reset();
    }

    @Test
    void shouldGenerateNonNullUrl_whenGeneratingPresignedPut() {
        StorageService.PresignedUploadDetails details =
                s3StorageService.generatePresignedPut("products/abc/images/def", "image/png");

        assertThat(details.uploadUrl()).isNotBlank();
        assertThat(details.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void shouldReturnMetadata_whenHeadObjectSucceeds() {
        String key = "products/abc/images/def";

        wireMock.stubFor(head(urlEqualTo("/test-bucket/" + key))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "image/png")
                        .withHeader("Content-Length", "1024")));

        StorageService.StorageObjectMetadata metadata = s3StorageService.verifyAndGetMetadata(key);

        assertThat(metadata.contentType()).isEqualTo("image/png");
        assertThat(metadata.sizeBytes()).isEqualTo(1024L);
    }

    @Test
    void shouldOpenCircuit_afterRepeatedFailures() {
        String key = "test-fail";
        wireMock.stubFor(head(urlEqualTo("/test-bucket/" + key))
                .willReturn(serviceUnavailable()));

        assertThatThrownBy(() -> s3StorageService.verifyAndGetMetadata(key))
                .isInstanceOf(StorageUnavailableException.class);

        assertThatThrownBy(() -> s3StorageService.verifyAndGetMetadata(key))
                .isInstanceOf(StorageUnavailableException.class);

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("object-storage");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> s3StorageService.verifyAndGetMetadata(key))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessageContaining("temporarily unavailable");

        // When OPEN, no more calls reach WireMock. 
        // Total requests = [attempts before OPEN] * [AWS SDK retries per attempt]
        // Default AWS SDK retry is 3 (4 total calls per attempt). 
        // With sliding window 2, we expect 2 * 4 = 8 calls.
        wireMock.verify(8, headRequestedFor(urlEqualTo("/test-bucket/" + key)));
    }

    @Test
    void shouldThrowStorageObjectNotFound_whenS3Returns404() {
        String key = "non-existent";
        wireMock.stubFor(head(urlEqualTo("/test-bucket/" + key))
                .willReturn(notFound()));

        assertThatThrownBy(() -> s3StorageService.verifyAndGetMetadata(key))
                .isInstanceOf(com.catalog.common.exception.StorageObjectNotFoundException.class);
    }
}
