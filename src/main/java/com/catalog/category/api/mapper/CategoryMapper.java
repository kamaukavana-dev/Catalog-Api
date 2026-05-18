package com.catalog.category.api.mapper;

import com.catalog.category.api.dto.response.CategoryResponse;
import com.catalog.category.api.dto.response.CategorySummaryResponse;
import com.catalog.category.domain.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(target = "parentId",
             expression = "java(category.getParent() != null ? category.getParent().getId() : null)")
    CategoryResponse toResponse(Category category);

    CategorySummaryResponse toSummaryResponse(Category category);
}

