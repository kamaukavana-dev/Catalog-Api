package com.catalog.variant.api.mapper;

import com.catalog.attribute.domain.AttributeValue;
import com.catalog.variant.api.dto.response.VariantResponse;
import com.catalog.variant.api.dto.response.VariantSummaryResponse;
import com.catalog.variant.domain.Variant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface VariantMapper {

    @Mapping(target = "productId", expression = "java(variant.getProduct().getId())")
    @Mapping(target = "effectivePrice", expression = "java(variant.getEffectivePrice())")
    @Mapping(target = "saleActive", expression = "java(variant.isSaleActive())")
    @Mapping(target = "attributes", expression = "java(mapAttributes(variant.getAttributeValues()))")
    VariantResponse toResponse(Variant variant);

    @Mapping(target = "effectivePrice", expression = "java(variant.getEffectivePrice())")
    @Mapping(target = "saleActive", expression = "java(variant.isSaleActive())")
    @Mapping(target = "attributes", expression = "java(mapAttributes(variant.getAttributeValues()))")
    VariantSummaryResponse toSummaryResponse(Variant variant);

    default List<VariantResponse.AttributeValueDetail> mapAttributes(Set<AttributeValue> attributeValues) {
        if (attributeValues == null) {
            return List.of();
        }
        return attributeValues.stream()
                .sorted(Comparator.comparingInt(av -> av.getAttributeType().getDisplayOrder()))
                .map(av -> new VariantResponse.AttributeValueDetail(
                        av.getAttributeType().getId(),
                        av.getAttributeType().getName(),
                        av.getAttributeType().getDisplayName(),
                        av.getId(),
                        av.getValue(),
                        av.getDisplayValue(),
                        av.getHexCode()
                ))
                .collect(Collectors.toList());
    }
}

