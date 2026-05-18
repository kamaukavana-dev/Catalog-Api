package com.catalog.attribute.application;

import com.catalog.attribute.api.dto.request.CreateAttributeTypeRequest;
import com.catalog.attribute.api.dto.request.CreateAttributeValueRequest;
import com.catalog.attribute.api.dto.response.AttributeTypeResponse;
import com.catalog.attribute.api.dto.response.AttributeValueResponse;
import com.catalog.attribute.api.mapper.AttributeMapper;
import com.catalog.attribute.domain.AttributeDataType;
import com.catalog.attribute.domain.AttributeType;
import com.catalog.attribute.domain.AttributeValue;
import com.catalog.attribute.infrastructure.AttributeTypeRepository;
import com.catalog.attribute.infrastructure.AttributeValueRepository;
import com.catalog.common.exception.BusinessRuleViolationException;
import com.catalog.common.exception.DuplicateResourceException;
import com.catalog.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttributeService {

    private final AttributeTypeRepository attributeTypeRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final AttributeMapper attributeMapper;

    @Transactional
    public AttributeTypeResponse createAttributeType(CreateAttributeTypeRequest request) {
        String normalizedName = normalizeKey(request.name());
        String normalizedDisplayName = normalizeDisplay(request.displayName());

        if (attributeTypeRepository.existsByName(normalizedName)) {
            throw new DuplicateResourceException(
                    "Attribute type with name '" + normalizedName + "' already exists."
            );
        }

        AttributeType attributeType = AttributeType.create(
                normalizedName,
                normalizedDisplayName,
                request.dataType()
        );

        if (request.unit() != null) {
            attributeType.setUnit(request.unit().trim());
        }
        if (request.filterable() != null) {
            attributeType.setFilterable(request.filterable());
        }
        if (request.displayOrder() != null) {
            attributeType.setDisplayOrder(request.displayOrder());
        }

        AttributeType saved = attributeTypeRepository.save(attributeType);
        return attributeMapper.toTypeResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AttributeTypeResponse> getAttributeTypes() {
        return attributeTypeRepository.findAllActive()
                .stream()
                .map(attributeMapper::toTypeResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AttributeValueResponse createAttributeValue(UUID typeId, CreateAttributeValueRequest request) {
        AttributeType type = attributeTypeRepository.findActiveById(typeId)
                .orElseThrow(() -> new ResourceNotFoundException("AttributeType", typeId));

        String normalizedValue = normalizeDisplay(request.value());
        String normalizedDisplayValue = normalizeDisplay(request.displayValue());

        if (attributeValueRepository.existsByTypeAndValue(typeId, normalizedValue)) {
            throw new DuplicateResourceException(
                    "Attribute value '" + normalizedValue + "' already exists for this type."
            );
        }

        AttributeValue value;
        if (type.getDataType() == AttributeDataType.COLOR) {
            if (request.hexCode() == null || request.hexCode().isBlank()) {
                throw new BusinessRuleViolationException("hexCode is required for COLOR attribute values.");
            }
            value = AttributeValue.createColor(type, normalizedValue, normalizedDisplayValue, request.hexCode());
        } else {
            if (request.hexCode() != null) {
                throw new BusinessRuleViolationException("hexCode can only be set for COLOR attribute values.");
            }
            value = AttributeValue.create(type, normalizedValue, normalizedDisplayValue);
        }

        if (request.displayOrder() != null) {
            value.setDisplayOrder(request.displayOrder());
        }

        AttributeValue saved = attributeValueRepository.save(value);
        return attributeMapper.toValueResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AttributeValueResponse> getAttributeValues(UUID typeId) {
        attributeTypeRepository.findActiveById(typeId)
                .orElseThrow(() -> new ResourceNotFoundException("AttributeType", typeId));

        return attributeValueRepository.findActiveByTypeId(typeId)
                .stream()
                .map(attributeMapper::toValueResponse)
                .collect(Collectors.toList());
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private String normalizeDisplay(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }
}

