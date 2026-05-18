package com.catalog.product.domain;

import com.catalog.common.exception.BusinessRuleViolationException;

import java.util.Set;

public enum ProductStatus {

    DRAFT {
        @Override
        public Set<ProductStatus> allowedTransitions() {
            return Set.of(ACTIVE, ARCHIVED);
        }
    },
    ACTIVE {
        @Override
        public Set<ProductStatus> allowedTransitions() {
            return Set.of(INACTIVE, ARCHIVED);
        }
    },
    INACTIVE {
        @Override
        public Set<ProductStatus> allowedTransitions() {
            return Set.of(ACTIVE, ARCHIVED);
        }
    },
    ARCHIVED {
        @Override
        public Set<ProductStatus> allowedTransitions() {
            return Set.of();
        }
    };

    public abstract Set<ProductStatus> allowedTransitions();

    public boolean canTransitionTo(ProductStatus target) {
        return allowedTransitions().contains(target);
    }

    public void assertCanTransitionTo(ProductStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessRuleViolationException(
                    String.format(
                            "Illegal product status transition: %s -> %s. Allowed transitions from %s: %s",
                            this,
                            target,
                            this,
                            allowedTransitions())
            );
        }
    }
}

