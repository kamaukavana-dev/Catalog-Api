package com.catalog.product.api.mapper;

import com.catalog.category.domain.Category;
import com.catalog.product.api.dto.response.ProductResponse;
import com.catalog.product.api.dto.response.ProductSummaryResponse;
import com.catalog.product.domain.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "primaryCategoryId", expression = "java(product.getPrimaryCategory() != null ? product.getPrimaryCategory().getId() : null)")
    @Mapping(target = "primaryCategoryName", expression = "java(product.getPrimaryCategory() != null ? product.getPrimaryCategory().getName() : null)")
    @Mapping(target = "brandId", expression = "java(product.getBrand() != null ? product.getBrand().getId() : null)")
    @Mapping(target = "brandName", expression = "java(product.getBrand() != null ? product.getBrand().getName() : null)")
    @Mapping(target = "secondaryCategoryIds", expression = "java(mapCategoryIds(product.getSecondaryCategories()))")
    ProductResponse toResponse(Product product);

    @Mapping(target = "primaryCategoryId", expression = "java(product.getPrimaryCategory() != null ? product.getPrimaryCategory().getId() : null)")
    @Mapping(target = "primaryCategoryName", expression = "java(product.getPrimaryCategory() != null ? product.getPrimaryCategory().getName() : null)")
    @Mapping(target = "brandId", expression = "java(product.getBrand() != null ? product.getBrand().getId() : null)")
    @Mapping(target = "brandName", expression = "java(product.getBrand() != null ? product.getBrand().getName() : null)")
    ProductSummaryResponse toSummaryResponse(Product product);

    default Set<UUID> mapCategoryIds(Set<Category> categories) {
        if (categories == null) {
            return Set.of();
        }
        return categories.stream()
                .map(Category::getId)
                .collect(Collectors.toSet());
    }
}

