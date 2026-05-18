package com.catalog.variant.domain;

import com.catalog.attribute.domain.AttributeValue;
import com.catalog.common.audit.BaseEntity;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.product.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "variants")
@Getter
@Setter
@NoArgsConstructor
public class Variant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(name = "internal_sku", nullable = false, length = 50, updatable = false)
    private String internalSku;

    @Column(name = "merchant_sku", length = 200)
    private String merchantSku;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VariantStatus status;

    @Column(name = "base_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal basePrice;

    @Column(name = "sale_price", precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Column(name = "sale_start_at")
    private Instant saleStartAt;

    @Column(name = "sale_end_at")
    private Instant saleEndAt;

    @Column(name = "cost_price", precision = 19, scale = 4)
    private BigDecimal costPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_class", nullable = false, length = 20)
    private TaxClass taxClass;

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "length_mm")
    private Integer lengthMm;

    @Column(name = "width_mm")
    private Integer widthMm;

    @Column(name = "height_mm")
    private Integer heightMm;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "variant_attribute_values",
            joinColumns = @JoinColumn(name = "variant_id"),
            inverseJoinColumns = @JoinColumn(name = "attribute_value_id")
    )
    private Set<AttributeValue> attributeValues = new HashSet<>();

    public static Variant createDraft(Product product, String internalSku, BigDecimal basePrice, TaxClass taxClass) {
        if (product == null) {
            throw new BusinessRuleViolationException("Product is required.");
        }
        if (internalSku == null || internalSku.isBlank()) {
            throw new BusinessRuleViolationException("Internal SKU is required.");
        }
        if (taxClass == null) {
            throw new BusinessRuleViolationException("Tax class is required.");
        }
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException("Base price must be greater than zero.");
        }
        Variant variant = new Variant();
        variant.product = product;
        variant.internalSku = internalSku.trim();
        variant.status = VariantStatus.DRAFT;
        variant.basePrice = basePrice;
        variant.taxClass = taxClass;
        return variant;
    }

    public void transitionTo(VariantStatus target) {
        this.status.assertCanTransitionTo(target);
        this.status = target;
    }

    public BigDecimal getEffectivePrice() {
        if (isSaleActive()) {
            return salePrice;
        }
        return basePrice;
    }

    public boolean isSaleActive() {
        if (salePrice == null) {
            return false;
        }
        Instant now = Instant.now();
        boolean afterStart = saleStartAt == null || now.isAfter(saleStartAt);
        boolean beforeEnd = saleEndAt == null || now.isBefore(saleEndAt);
        return afterStart && beforeEnd;
    }

    public boolean isPurchasable(boolean productIsActive) {
        return productIsActive && this.status == VariantStatus.ACTIVE && !isDeleted();
    }

    public void setSalePrice(BigDecimal salePrice, Instant startAt, Instant endAt) {
        if (salePrice != null && salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException("Sale price must be greater than zero.");
        }
        if (salePrice != null && startAt == null && endAt == null) {
            throw new BusinessRuleViolationException(
                    "Sale price requires at least one time bound (start or end)."
            );
        }
        if (startAt != null && endAt != null && !startAt.isBefore(endAt)) {
            throw new BusinessRuleViolationException("Sale start must be before sale end.");
        }
        this.salePrice = salePrice;
        this.saleStartAt = startAt;
        this.saleEndAt = endAt;
    }
}

