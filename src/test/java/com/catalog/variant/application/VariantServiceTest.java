package com.catalog.variant.application;

import com.catalog.attribute.domain.AttributeDataType;
import com.catalog.attribute.domain.AttributeType;
import com.catalog.attribute.domain.AttributeValue;
import com.catalog.attribute.infrastructure.AttributeValueRepository;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.product.domain.Product;
import com.catalog.product.infrastructure.ProductRepository;
import com.catalog.variant.api.dto.request.CreateVariantRequest;
import com.catalog.variant.api.mapper.VariantMapper;
import com.catalog.variant.domain.TaxClass;
import com.catalog.variant.domain.Variant;
import com.catalog.variant.infrastructure.VariantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VariantServiceTest {

    @Mock
    private VariantRepository variantRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AttributeValueRepository attributeValueRepository;

    @Mock
    private VariantMapper variantMapper;

    @Mock
    private com.catalog.common.util.SkuGenerator skuGenerator;

    @InjectMocks
    private VariantService variantService;

    @Test
    void shouldRejectDuplicateAttributeCombination() {
        UUID productId = UUID.randomUUID();
        UUID colorId = UUID.randomUUID();
        UUID sizeId = UUID.randomUUID();

        Product product = Product.createDraft("Test", "test");
        when(productRepository.findActiveById(productId)).thenReturn(Optional.of(product));

        AttributeType colorType = AttributeType.create("color", "Color", AttributeDataType.COLOR);
        colorType.setId(UUID.randomUUID());
        colorType.setDisplayOrder(1);

        AttributeType sizeType = AttributeType.create("size", "Size", AttributeDataType.TEXT);
        sizeType.setId(UUID.randomUUID());
        sizeType.setDisplayOrder(2);

        AttributeValue blue = AttributeValue.createColor(colorType, "blue", "Blue", "#0000FF");
        blue.setId(colorId);
        AttributeValue medium = AttributeValue.create(sizeType, "medium", "Medium");
        medium.setId(sizeId);

        when(attributeValueRepository.findActiveByIds(Set.of(colorId, sizeId)))
                .thenReturn(Set.of(blue, medium));

        Variant existing = Variant.createDraft(product, "VAR-EXIST", BigDecimal.TEN, TaxClass.STANDARD);
        existing.setId(UUID.randomUUID());
        existing.getAttributeValues().add(blue);
        existing.getAttributeValues().add(medium);

        when(variantRepository.findActiveByProductIdWithAttributes(productId)).thenReturn(List.of(existing));

        CreateVariantRequest request = new CreateVariantRequest(
                null,
                BigDecimal.valueOf(50),
                null,
                null,
                null,
                null,
                TaxClass.STANDARD,
                Set.of(colorId, sizeId),
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> variantService.createVariant(productId, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("exact attribute combination already exists");
    }

    @Test
    void shouldRejectMultipleValuesForSameAttributeType() {
        UUID productId = UUID.randomUUID();
        UUID blueId = UUID.randomUUID();
        UUID redId = UUID.randomUUID();

        Product product = Product.createDraft("Test", "test");
        when(productRepository.findActiveById(productId)).thenReturn(Optional.of(product));

        AttributeType colorType = AttributeType.create("color", "Color", AttributeDataType.COLOR);
        colorType.setId(UUID.randomUUID());

        AttributeValue blue = AttributeValue.createColor(colorType, "blue", "Blue", "#0000FF");
        blue.setId(blueId);
        AttributeValue red = AttributeValue.createColor(colorType, "red", "Red", "#FF0000");
        red.setId(redId);

        when(attributeValueRepository.findActiveByIds(Set.of(blueId, redId)))
                .thenReturn(Set.of(blue, red));

        CreateVariantRequest request = new CreateVariantRequest(
                null,
                BigDecimal.valueOf(50),
                null,
                null,
                null,
                null,
                TaxClass.STANDARD,
                Set.of(blueId, redId),
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> variantService.createVariant(productId, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("multiple values for the same attribute type");
    }
}

