package com.catalog.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitingFilterTest {

    private static ObjectMapper jsonMapper() {
        return new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @Test
    void shouldReturn429WhenRedisDeniesRequest() throws Exception {
        RedisTokenBucketRateLimiter limiter = mock(RedisTokenBucketRateLimiter.class);
        when(limiter.tryConsume(any(), anyInt(), any())).thenReturn(false);

        RateLimitingFilter filter = new RateLimitingFilter(limiter, jsonMapper(), 1);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void shouldFallbackToInMemoryWhenRedisUnavailable() throws Exception {
        RedisTokenBucketRateLimiter limiter = mock(RedisTokenBucketRateLimiter.class);
        doThrow(new RuntimeException("redis-down"))
                .when(limiter).tryConsume(any(), anyInt(), any());

        RateLimitingFilter filter = new RateLimitingFilter(limiter, jsonMapper(), 1);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void shouldBypassActuatorEndpoints() throws Exception {
        RedisTokenBucketRateLimiter limiter = mock(RedisTokenBucketRateLimiter.class);
        when(limiter.tryConsume(any(), anyInt(), any())).thenReturn(false);

        RateLimitingFilter filter = new RateLimitingFilter(limiter, jsonMapper(), 1);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void shouldIgnoreSpoofedLeftmostXForwardedForEntry() throws Exception {
        // With one trusted proxy, the genuine client IP is the rightmost X-Forwarded-For
        // entry (appended by that proxy). A client that rotates the leftmost, spoofed entry
        // must still be bucketed by the same real IP — otherwise rate limiting is bypassed.
        RedisTokenBucketRateLimiter limiter = mock(RedisTokenBucketRateLimiter.class);
        when(limiter.tryConsume(any(), anyInt(), any())).thenReturn(true);

        RateLimitingFilter filter = new RateLimitingFilter(limiter, jsonMapper(), 1);

        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/v1/products");
        first.addHeader("X-Forwarded-For", "9.9.9.9, 8.8.8.8");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/v1/products");
        second.addHeader("X-Forwarded-For", "7.7.7.7, 8.8.8.8");
        filter.doFilter(second, new MockHttpServletResponse(), new MockFilterChain());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter, times(2)).tryConsume(keyCaptor.capture(), eq(100), eq(Duration.ofMinutes(1)));

        // Both requests resolve to the real client (8.8.8.8); the spoofed leftmost values
        // (9.9.9.9 / 7.7.7.7) never appear in the rate-limit key.
        assertThat(keyCaptor.getAllValues())
                .allSatisfy(key -> assertThat(key).contains("8.8.8.8"))
                .noneMatch(key -> key.contains("9.9.9.9"))
                .noneMatch(key -> key.contains("7.7.7.7"));
        assertThat(keyCaptor.getAllValues().get(0)).isEqualTo(keyCaptor.getAllValues().get(1));
    }
}
