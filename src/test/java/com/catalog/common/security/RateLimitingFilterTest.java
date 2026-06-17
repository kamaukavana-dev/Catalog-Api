package com.catalog.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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

        RateLimitingFilter filter = new RateLimitingFilter(limiter, jsonMapper());
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

        RateLimitingFilter filter = new RateLimitingFilter(limiter, jsonMapper());
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

        RateLimitingFilter filter = new RateLimitingFilter(limiter, jsonMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(429);
    }
}
