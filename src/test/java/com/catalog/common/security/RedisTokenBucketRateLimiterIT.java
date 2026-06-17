package com.catalog.common.security;

import com.catalog.common.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// BaseIntegrationTest no longer carries @Testcontainers (its Postgres is a manually
// started singleton), so this class declares @Testcontainers itself to manage the
// Redis @Container below. The inherited Postgres field has no @Container and is
// therefore left untouched by this extension.
@Testcontainers
class RedisTokenBucketRateLimiterIT extends BaseIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("catalog.storage.bucket", () -> "test-bucket");
        registry.add("catalog.storage.endpoint", () -> "http://localhost:9000");
        registry.add("catalog.storage.access-key", () -> "test-access");
        registry.add("catalog.storage.secret-key", () -> "test-secret");
    }

    @Autowired
    private RedisTokenBucketRateLimiter rateLimiter;

    @Test
    void enforcesTokenBucketLimit() {
        String key = "rate-limit:test-client:read";
        // Use a one-minute refill window (as the production filter does). With a
        // 1-second window the bucket refills at capacity tokens/sec, which is fast
        // enough that Redis connection + Lua-load latency on the first call lets a
        // whole token regenerate before the third call — masking the limit and making
        // the assertion flaky. A minute window refills ~0.03 tokens/sec: negligible
        // across the test's runtime, so the capacity limit is exercised deterministically.
        Duration period = Duration.ofMinutes(1);

        assertThat(rateLimiter.tryConsume(key, 2, period)).isTrue();
        assertThat(rateLimiter.tryConsume(key, 2, period)).isTrue();
        assertThat(rateLimiter.tryConsume(key, 2, period)).isFalse();
    }
}

