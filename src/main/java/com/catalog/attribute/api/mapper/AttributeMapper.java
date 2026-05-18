package com.catalog.attribute.api.mapper;

import com.catalog.attribute.api.dto.response.AttributeTypeResponse;
import com.catalog.attribute.api.dto.response.AttributeValueResponse;
import com.catalog.attribute.domain.AttributeType;
import com.catalog.attribute.domain.AttributeValue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AttributeMapper {

    AttributeTypeResponse toTypeResponse(AttributeType attributeType);

    @Mapping(target = "attributeTypeId", expression = "java(attributeValue.getAttributeType().getId())")
    AttributeValueResponse toValueResponse(AttributeValue attributeValue);
}

