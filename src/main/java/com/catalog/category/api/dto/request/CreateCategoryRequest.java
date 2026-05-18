package com.catalog.category.api.dto.request;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record CreateCategoryRequest(

    @NotBlank(message = "Category name is required")
    @Size(min = 2, max = 200, message = "Name must be between 2 and 200 characters")
    String name,

    @Size(max = 200, message = "Slug must not exceed 200 characters")
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
             message = "Slug must be lowercase alphanumeric with hyphens only")
    String slug,

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    String description,

    UUID parentId,

    @Min(value = 0, message = "Display order must be non-negative")
    @Max(value = 9999, message = "Display order must not exceed 9999")
    Integer displayOrder,

    @Size(max = 1000, message = "Image URL must not exceed 1000 characters")
    String imageUrl,

    @Size(max = 200, message = "Meta title must not exceed 200 characters")
    String metaTitle,

    @Size(max = 500, message = "Meta description must not exceed 500 characters")
    String metaDescription
) {}

