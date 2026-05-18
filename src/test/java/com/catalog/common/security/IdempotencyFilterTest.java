package com.catalog.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdempotencyFilterTest {

    @Test
    void rejectsReplayWhenPayloadDiffers() throws Exception {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ObjectMapper mapper = new ObjectMapper();
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
}

