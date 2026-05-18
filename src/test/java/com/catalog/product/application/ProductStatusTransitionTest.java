package com.catalog.product.application;

import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.product.domain.Product;
import com.catalog.product.domain.ProductStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductStatusTransitionTest {

    @Test
    void draftCanTransitionToActiveWhenPreconditionsSatisfied() {
        Product product = Product.createDraft("Test Product", "test-product");

        assertThat(ProductStatus.DRAFT.canTransitionTo(ProductStatus.ACTIVE)).isTrue();
        assertThatThrownBy(() -> product.transitionTo(ProductStatus.ACTIVE))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("primary category");
    }

    @Test
    void archivedCannotTransitionToAnything() {
        assertThat(ProductStatus.ARCHIVED.canTransitionTo(ProductStatus.ACTIVE)).isFalse();
        assertThat(ProductStatus.ARCHIVED.canTransitionTo(ProductStatus.INACTIVE)).isFalse();
        assertThat(ProductStatus.ARCHIVED.canTransitionTo(ProductStatus.DRAFT)).isFalse();
    }

    @Test
    void archivedAssertThrowsOnAnyTransition() {
        assertThatThrownBy(() -> ProductStatus.ARCHIVED.assertCanTransitionTo(ProductStatus.ACTIVE))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("ARCHIVED");
    }

    @Test
    void activeCanOnlyTransitionToInactiveOrArchived() {
        assertThat(ProductStatus.ACTIVE.canTransitionTo(ProductStatus.INACTIVE)).isTrue();
        assertThat(ProductStatus.ACTIVE.canTransitionTo(ProductStatus.ARCHIVED)).isTrue();
        assertThat(ProductStatus.ACTIVE.canTransitionTo(ProductStatus.DRAFT)).isFalse();
    }
}

