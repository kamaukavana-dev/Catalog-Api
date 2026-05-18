package com.catalog.brand.api.mapper;

import com.catalog.brand.api.dto.response.BrandResponse;
import com.catalog.brand.api.dto.response.BrandSummaryResponse;
import com.catalog.brand.domain.Brand;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BrandMapper {

    BrandResponse toResponse(Brand brand);

    BrandSummaryResponse toSummaryResponse(Brand brand);
}

