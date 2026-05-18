package com.catalog.variant.domain;

import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.product.domain.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class VariantPricingTest {

    @Test
    void effectivePriceReturnsSaleWhenActive() {
        Variant variant = Variant.createDraft(mock(Product.class), "VAR-001",
                BigDecimal.valueOf(100), TaxClass.STANDARD);
        variant.setSalePrice(BigDecimal.valueOf(80),
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(3600));

        assertThat(variant.getEffectivePrice()).isEqualByComparingTo("80");
        assertThat(variant.isSaleActive()).isTrue();
    }

    @Test
    void effectivePriceReturnsBaseWhenSaleExpired() {
        Variant variant = Variant.createDraft(mock(Product.class), "VAR-002",
                BigDecimal.valueOf(100), TaxClass.STANDARD);
        variant.setSalePrice(BigDecimal.valueOf(80),
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600));

        assertThat(variant.getEffectivePrice()).isEqualByComparingTo("100");
        assertThat(variant.isSaleActive()).isFalse();
    }

    @Test
    void saleWithoutTimeBoundsShouldBeRejected() {
        Variant variant = Variant.createDraft(mock(Product.class), "VAR-003",
                BigDecimal.valueOf(100), TaxClass.STANDARD);

        assertThatThrownBy(() -> variant.setSalePrice(BigDecimal.valueOf(80), null, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("time bound");
    }
}

