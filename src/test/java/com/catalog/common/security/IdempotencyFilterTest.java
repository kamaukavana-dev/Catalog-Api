package com.catalog.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyFilterTest {

    @Test
    void rejectsReplayWhenPayloadDiffers() throws Exception {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        Map<String, Object> cached = new HashMap<>();
        cached.put("status", 201);
        cached.put("contentType", "application/json");
        cached.put("location", "/api/v1/products/1");
        cached.put("body", "{\"ok\":true}");
        cached.put("requestHash", "DIFFERENT_HASH");

        when(valueOperations.get(anyString())).thenReturn(mapper.writeValueAsString(cached));

        IdempotencyFilter filter = new IdempotencyFilter(redisTemplate, mapper);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/products");
        request.addHeader("X-Idempotency-Key", "abc-123");
        request.setContentType("application/json");
        request.setContent("{\"name\":\"Product A\"}".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("Idempotency Conflict");
    }

    @Test
    @SuppressWarnings("unchecked")
    void failsClosedWithA503WhenRedisIsDownForStockEndpoint() throws Exception {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        ObjectMapper mapper = jsonMapper();
        IdempotencyFilter filter = new IdempotencyFilter(redisTemplate, mapper);

        MockHttpServletRequest request = mutation("POST", "/api/v1/inventory/reservations",
                "{\"variantId\":\"v\",\"quantity\":1}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        // Duplicating a reservation oversells stock and no DB constraint would catch it,
        // so with the store unavailable the request is refused rather than risked.
        assertThat(response.getStatus()).isEqualTo(503);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void failsOpenWhenRedisIsDownForDbGuardedCatalogEndpoint() throws Exception {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        IdempotencyFilter filter = new IdempotencyFilter(redisTemplate, jsonMapper());

        MockHttpServletRequest request = mutation("POST", "/api/v1/products", "{\"name\":\"P\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger executed = new AtomicInteger();
        FilterChain chain = (req, res) -> {
            executed.incrementAndGet();
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(201);
        };

        filter.doFilter(request, response, chain);

        // A duplicate product create is rejected by the unique-slug constraint, so it is safe
        // to proceed without the idempotency guard rather than reject a legitimate request.
        assertThat(executed.get()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(201);
    }

    private static ObjectMapper jsonMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        return mapper;
    }

    private static MockHttpServletRequest mutation(String method, String uri, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(uri);
        request.addHeader("X-Idempotency-Key", "key-" + uri.hashCode());
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}

