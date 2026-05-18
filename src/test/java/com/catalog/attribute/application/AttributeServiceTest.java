package com.catalog.attribute.application;

import com.catalog.attribute.api.dto.request.CreateAttributeTypeRequest;
import com.catalog.attribute.api.dto.request.CreateAttributeValueRequest;
import com.catalog.attribute.api.mapper.AttributeMapper;
import com.catalog.attribute.domain.AttributeDataType;
import com.catalog.attribute.domain.AttributeType;
import com.catalog.attribute.infrastructure.AttributeTypeRepository;
import com.catalog.attribute.infrastructure.AttributeValueRepository;
import com.catalog.common.exception.DuplicateResourceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributeServiceTest {

    @Mock
    private AttributeTypeRepository attributeTypeRepository;

    @Mock
    private AttributeValueRepository attributeValueRepository;

    @Mock
    private AttributeMapper attributeMapper;

    @InjectMocks
    private AttributeService attributeService;

    @Test
    void shouldNormalizeAttributeTypeNameBeforeDuplicateCheck() {
        when(attributeTypeRepository.existsByName("color")).thenReturn(true);

        CreateAttributeTypeRequest request = new CreateAttributeTypeRequest(
                "  Color  ",
                "Color",
                AttributeDataType.TEXT,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> attributeService.createAttributeType(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("color");

        verify(attributeTypeRepository).existsByName("color");
    }

    @Test
    void shouldNormalizeAttributeValueBeforeDuplicateCheck() {
        UUID typeId = UUID.randomUUID();
        AttributeType type = AttributeType.create("color", "Color", AttributeDataType.TEXT);
        type.setId(typeId);

        when(attributeTypeRepository.findActiveById(typeId)).thenReturn(Optional.of(type));
        when(attributeValueRepository.existsByTypeAndValue(typeId, "Blue")).thenReturn(true);

        CreateAttributeValueRequest request = new CreateAttributeValueRequest(
                "  Blue  ",
                " Blue ",
                null,
                null
        );

        assertThatThrownBy(() -> attributeService.createAttributeValue(typeId, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Blue");

        verify(attributeValueRepository).existsByTypeAndValue(typeId, "Blue");
    }
}

