package com.catalog.brand.api.dto.request;

import jakarta.validation.constraints.*;

public record CreateBrandRequest(

    @NotBlank(message = "Brand name is required")
    @Size(min = 1, max = 200, message = "Name must be between 1 and 200 characters")
    String name,

    @Size(max = 200)
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
             message = "Slug must be lowercase alphanumeric with hyphens only")
    String slug,

    @Size(max = 5000)
    String description,

    @Size(max = 1000, message = "Logo URL must not exceed 1000 characters")
    String logoUrl,

    @Size(max = 1000, message = "Website URL must not exceed 1000 characters")
    String websiteUrl,

    @Size(max = 100, message = "Country of origin must not exceed 100 characters")
    String countryOfOrigin,

    @Min(value = 1800, message = "Founded year must be 1800 or later")
    Integer foundedYear,

    Boolean featured
) {}

