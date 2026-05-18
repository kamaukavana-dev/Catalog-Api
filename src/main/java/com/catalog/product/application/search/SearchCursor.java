package com.catalog.product.application.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public record SearchCursor(
        @JsonProperty("ts") Instant createdAt,
        @JsonProperty("n") String name,
        @JsonProperty("p") BigDecimal price,
        @JsonProperty("id") UUID id
) {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public String encode() {
        try {
            String json = MAPPER.writeValueAsString(this);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode cursor", e);
        }
    }

    public static SearchCursor decode(String encoded) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            return MAPPER.readValue(new String(decoded, StandardCharsets.UTF_8), SearchCursor.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor token: cannot be decoded", e);
        }
    }

    public static SearchCursor of(Instant createdAt, String name, BigDecimal price, UUID id) {
        return new SearchCursor(createdAt, name, price, id);
    }
}

