package com.catalog.attribute.domain;

import com.catalog.common.audit.BaseEntity;
import com.catalog.common.exception.BusinessRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "attribute_values")
@Getter
@Setter
@NoArgsConstructor
public class AttributeValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_type_id", nullable = false, updatable = false)
    private AttributeType attributeType;

    @Column(name = "value", nullable = false, length = 200)
    private String value;

    @Column(name = "display_value", nullable = false, length = 200)
    private String displayValue;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "hex_code", length = 7)
    private String hexCode;

    public static AttributeValue create(AttributeType type, String value, String displayValue) {
        if (type == null) {
            throw new BusinessRuleViolationException("Attribute type is required.");
        }
        if (type.getDataType() == AttributeDataType.COLOR) {
            throw new BusinessRuleViolationException("Use createColor() for COLOR type attribute values.");
        }
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException("Attribute value is required.");
        }
        if (displayValue == null || displayValue.isBlank()) {
            throw new BusinessRuleViolationException("Attribute display value is required.");
        }

        AttributeValue attributeValue = new AttributeValue();
        attributeValue.attributeType = type;
        attributeValue.value = value.trim();
        attributeValue.displayValue = displayValue.trim();
        attributeValue.displayOrder = 0;
        return attributeValue;
    }

    public static AttributeValue createColor(AttributeType type, String value, String displayValue, String hexCode) {
        if (type == null) {
            throw new BusinessRuleViolationException("Attribute type is required.");
        }
        if (type.getDataType() != AttributeDataType.COLOR) {
            throw new BusinessRuleViolationException("createColor() is only valid for COLOR type attribute values.");
        }
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException("Attribute value is required.");
        }
        if (displayValue == null || displayValue.isBlank()) {
            throw new BusinessRuleViolationException("Attribute display value is required.");
        }
        if (hexCode == null || hexCode.isBlank()) {
            throw new BusinessRuleViolationException("Hex code is required for COLOR attribute values.");
        }

        AttributeValue attributeValue = new AttributeValue();
        attributeValue.attributeType = type;
        attributeValue.value = value.trim();
        attributeValue.displayValue = displayValue.trim();
        attributeValue.hexCode = hexCode.trim();
        attributeValue.displayOrder = 0;
        return attributeValue;
    }
}

