package com.catalog.variant.domain;

import com.catalog.common.exception.BusinessRuleViolationException;

import java.util.Set;

public enum VariantStatus {

    DRAFT {
        @Override
        public Set<VariantStatus> allowedTransitions() {
            return Set.of(ACTIVE, ARCHIVED);
        }
    },
    ACTIVE {
        @Override
        public Set<VariantStatus> allowedTransitions() {
            return Set.of(DISCONTINUED, ARCHIVED);
        }
    },
    DISCONTINUED {
        @Override
        public Set<VariantStatus> allowedTransitions() {
            return Set.of(ACTIVE, ARCHIVED);
        }
    },
    ARCHIVED {
        @Override
        public Set<VariantStatus> allowedTransitions() {
            return Set.of();
        }
    };

    public abstract Set<VariantStatus> allowedTransitions();

    public boolean canTransitionTo(VariantStatus target) {
        return allowedTransitions().contains(target);
    }

    public void assertCanTransitionTo(VariantStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessRuleViolationException(
                    String.format(
                            "Illegal variant status transition: %s -> %s. Allowed from %s: %s",
                            this,
                            target,
                            this,
                            allowedTransitions()
                    )
            );
        }
    }
}

