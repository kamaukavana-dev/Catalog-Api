package com.catalog.common.security;

import com.catalog.common.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the idempotency filter's atomic claim admits exactly one execution under a
 * concurrent burst of identical retries — the race the previous check-then-write design
 * allowed. Uses a real Redis so the {@code SET NX PX} atomicity is genuinely exercised;
 * a mocked template cannot demonstrate it.
 */
@Testcontainers
class IdempotencyConcurrencyIT extends BaseIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("catalog.storage.bucket", () -> "test-bucket");
        registry.add("catalog.storage.endpoint", () -> "http://localhost:9000");
        registry.add("catalog.storage.access-key", () -> "test-access");
        registry.add("catalog.storage.secret-key", () -> "test-secret");
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exactlyOneOfManyConcurrentIdenticalRequestsExecutes() throws Exception {
        IdempotencyFilter filter = new IdempotencyFilter(stringRedisTemplate, objectMapper);

        int threads = 24;
        String idempotencyKey = UUID.randomUUID().toString();
        String body = "{\"variantId\":\"11111111-1111-1111-1111-111111111111\",\"quantity\":1}";

        AtomicInteger executions = new AtomicInteger();
        List<Integer> statuses = new CopyOnWriteArrayList<>();

        // The "downstream" simulates a successful mutation: it counts real executions and
        // returns 201. Losers of the claim must never reach this chain.
        FilterChain chain = (req, res) -> {
            executions.incrementAndGet();
            HttpServletResponse http = (HttpServletResponse) res;
            http.setStatus(201);
            http.getWriter().write("{\"reservationId\":\"" + UUID.randomUUID() + "\"}");
        };

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                MockHttpServletRequest request = new MockHttpServletRequest();
                request.setMethod("POST");
                request.setRequestURI("/api/v1/inventory/reservations");
                request.addHeader("X-Idempotency-Key", idempotencyKey);
                request.setContentType("application/json");
                request.setContent(body.getBytes(StandardCharsets.UTF_8));
                MockHttpServletResponse response = new MockHttpServletResponse();
                try {
                    ready.countDown();
                    go.await();
                    filter.doFilter(request, response, chain);
                    statuses.add(response.getStatus());
                } catch (Exception e) {
                    statuses.add(-1);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        go.countDown(); // release all threads at once for maximum contention
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // The whole point: the mutation ran exactly once despite 24 identical concurrent
        // retries. This is the invariant that oversell/duplication depends on, and it holds
        // deterministically because SET NX admits exactly one claimant.
        assertThat(executions.get()).isEqualTo(1);
        // Every caller either got the single real 201, replayed that cached 201, or was
        // rejected as an in-progress duplicate (409) — nothing else, and no second execution.
        assertThat(statuses).hasSize(threads);
        assertThat(statuses).allSatisfy(s -> assertThat(s).isIn(201, 409));
        // At least one caller was rejected as a duplicate (the losers of the claim race).
        assertThat(statuses.stream().filter(s -> s == 409).count()).isGreaterThanOrEqualTo(1);
    }
}
