package com.catalog.attribute.application;

import com.catalog.attribute.api.dto.request.CreateAttributeTypeRequest;
import com.catalog.attribute.api.dto.request.CreateAttributeValueRequest;
import com.catalog.attribute.api.dto.response.AttributeTypeResponse;
import com.catalog.attribute.api.dto.response.AttributeValueResponse;
import com.catalog.attribute.domain.AttributeDataType;
import com.catalog.common.BaseIntegrationTest;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributeServiceIT extends BaseIntegrationTest {

    @Autowired
    private AttributeService attributeService;

    private AttributeTypeResponse createType(String name, AttributeDataType dataType) {
        return attributeService.createAttributeType(
                new CreateAttributeTypeRequest(name, name + " Display", dataType, null, true, 1));
    }

    @Test
    void createsAttributeTypeNormalizingNameToLowercase() {
        AttributeTypeResponse res = createType("Material", AttributeDataType.TEXT);

        assertThat(res.name()).isEqualTo("material");
        assertThat(res.dataType()).isEqualTo(AttributeDataType.TEXT);
        assertThat(res.filterable()).isTrue();
    }

    @Test
    void rejectsDuplicateAttributeTypeName() {
        createType("Size", AttributeDataType.TEXT);
        assertThatThrownBy(() -> createType("size", AttributeDataType.TEXT))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void listsActiveAttributeTypes() {
        createType("Weight", AttributeDataType.NUMBER);
        createType("Waterproof", AttributeDataType.BOOLEAN);

        assertThat(attributeService.getAttributeTypes())
                .extracting(AttributeTypeResponse::name)
                .contains("weight", "waterproof");
    }

    @Test
    void createsTextAttributeValue() {
        AttributeTypeResponse type = createType("Fabric", AttributeDataType.TEXT);

        AttributeValueResponse value = attributeService.createAttributeValue(type.id(),
                new CreateAttributeValueRequest("cotton", "Cotton", null, 1));

        assertThat(value.value()).isEqualTo("cotton");
        assertThat(value.attributeTypeId()).isEqualTo(type.id());
        assertThat(value.hexCode()).isNull();
    }

    @Test
    void colorValueRequiresHexCode() {
        AttributeTypeResponse type = createType("Colour", AttributeDataType.COLOR);

        assertThatThrownBy(() -> attributeService.createAttributeValue(type.id(),
                new CreateAttributeValueRequest("red", "Red", null, 1)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("hexCode is required");
    }

    @Test
    void colorValueWithHexCodeIsStored() {
        AttributeTypeResponse type = createType("Shade", AttributeDataType.COLOR);

        AttributeValueResponse value = attributeService.createAttributeValue(type.id(),
                new CreateAttributeValueRequest("red", "Red", "#FF0000", 1));

        assertThat(value.hexCode()).isEqualTo("#FF0000");
    }

    @Test
    void hexCodeRejectedForNonColorType() {
        AttributeTypeResponse type = createType("Grade", AttributeDataType.TEXT);

        assertThatThrownBy(() -> attributeService.createAttributeValue(type.id(),
                new CreateAttributeValueRequest("a", "A", "#FF0000", 1)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("hexCode can only be set");
    }

    @Test
    void rejectsDuplicateValueWithinType() {
        AttributeTypeResponse type = createType("Finish", AttributeDataType.TEXT);
        attributeService.createAttributeValue(type.id(),
                new CreateAttributeValueRequest("matte", "Matte", null, 1));

        assertThatThrownBy(() -> attributeService.createAttributeValue(type.id(),
                new CreateAttributeValueRequest("matte", "Matte", null, 2)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void creatingValueForUnknownTypeThrowsNotFound() {
        assertThatThrownBy(() -> attributeService.createAttributeValue(UUID.randomUUID(),
                new CreateAttributeValueRequest("x", "X", null, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listingValuesForUnknownTypeThrowsNotFound() {
        assertThatThrownBy(() -> attributeService.getAttributeValues(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listsValuesForAType() {
        AttributeTypeResponse type = createType("Pattern", AttributeDataType.TEXT);
        attributeService.createAttributeValue(type.id(), new CreateAttributeValueRequest("striped", "Striped", null, 1));
        attributeService.createAttributeValue(type.id(), new CreateAttributeValueRequest("solid", "Solid", null, 2));

        assertThat(attributeService.getAttributeValues(type.id()))
                .extracting(AttributeValueResponse::value)
                .containsExactlyInAnyOrder("striped", "solid");
    }
}
