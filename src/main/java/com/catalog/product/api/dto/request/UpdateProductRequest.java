package com.catalog.product.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateProductRequest(

        @NotBlank(message = "Product name is required")
        @Size(min = 2, max = 300)
        String name,

        @Size(max = 300)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "Slug must be lowercase alphanumeric with hyphens only")
        String slug,

        @Size(max = 1000)
        String shortDescription,

        String description,

        UUID primaryCategoryId,

        UUID brandId,

        @Size(max = 200)
        String metaTitle,

        @Size(max = 500)
        String metaDescription
) {
}

