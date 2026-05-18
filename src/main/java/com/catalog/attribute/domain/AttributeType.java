package com.catalog.attribute.domain;

import com.catalog.common.audit.BaseEntity;
import com.catalog.common.exception.BusinessRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "attribute_types")
@Getter
@Setter
@NoArgsConstructor
public class AttributeType extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private AttributeDataType dataType;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "is_filterable", nullable = false)
    private boolean filterable = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @OneToMany(mappedBy = "attributeType", fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<AttributeValue> values = new ArrayList<>();

    public static AttributeType create(String name, String displayName, AttributeDataType dataType) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleViolationException("Attribute type name is required.");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new BusinessRuleViolationException("Attribute type display name is required.");
        }
        if (dataType == null) {
            throw new BusinessRuleViolationException("Attribute type data type is required.");
        }

        AttributeType type = new AttributeType();
        type.name = name.trim().toLowerCase();
        type.displayName = displayName.trim();
        type.dataType = dataType;
        type.filterable = true;
        type.displayOrder = 0;
        return type;
    }
}

