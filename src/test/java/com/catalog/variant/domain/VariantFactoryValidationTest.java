package com.catalog.variant.domain;

import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.product.domain.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class VariantFactoryValidationTest {

    @Test
    void createDraftShouldRejectMissingTaxClass() {
        assertThatThrownBy(() -> Variant.createDraft(mock(Product.class), "VAR-001", BigDecimal.ONE, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Tax class is required");
    }

    @Test
    void createDraftShouldRejectBlankInternalSku() {
        assertThatThrownBy(() -> Variant.createDraft(mock(Product.class), "   ", BigDecimal.ONE, TaxClass.STANDARD))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Internal SKU is required");
    }
}

