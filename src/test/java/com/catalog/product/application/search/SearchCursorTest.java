package com.catalog.product.application.search;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCursorTest {

    @Test
    void encodesAndDecodesSymmetrically() {
        UUID id = UUID.randomUUID();
        Instant ts = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        SearchCursor cursor = SearchCursor.of(ts, "Test Product", new BigDecimal("19.99"), id);

        SearchCursor decoded = SearchCursor.decode(cursor.encode());
        assertThat(decoded.id()).isEqualTo(id);
        assertThat(decoded.createdAt()).isEqualTo(ts);
        assertThat(decoded.name()).isEqualTo("Test Product");
        assertThat(decoded.price()).isEqualTo(new BigDecimal("19.99"));
    }

    @Test
    void invalidCursorThrowsIllegalArgument() {
        assertThatThrownBy(() -> SearchCursor.decode("not-valid-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid cursor");
    }
}

