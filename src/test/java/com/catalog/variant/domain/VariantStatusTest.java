package com.catalog.variant.domain;

import com.catalog.common.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariantStatusTest {

    @Test
    void archivedIsTerminal() {
        assertThat(VariantStatus.ARCHIVED.allowedTransitions()).isEmpty();
        assertThatThrownBy(() -> VariantStatus.ARCHIVED.assertCanTransitionTo(VariantStatus.ACTIVE))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void discontinuedCanBeReactivated() {
        assertThat(VariantStatus.DISCONTINUED.canTransitionTo(VariantStatus.ACTIVE)).isTrue();
    }

    @Test
    void draftCannotGoToDiscontinued() {
        assertThat(VariantStatus.DRAFT.canTransitionTo(VariantStatus.DISCONTINUED)).isFalse();
    }
}

