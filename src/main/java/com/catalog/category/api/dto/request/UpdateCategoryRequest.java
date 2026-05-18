package com.catalog.category.api.dto.request;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record UpdateCategoryRequest(

    @NotBlank(message = "Category name is required")
    @Size(min = 2, max = 200)
    String name,

    @Size(max = 200)
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
             message = "Slug must be lowercase alphanumeric with hyphens only")
    String slug,

    @Size(max = 5000)
    String description,

    UUID parentId,

    @Min(0)
    @Max(9999)
    Integer displayOrder,

    Boolean active,

    @Size(max = 1000)
    String imageUrl,

    @Size(max = 200)
    String metaTitle,

    @Size(max = 500)
    String metaDescription
) {}

