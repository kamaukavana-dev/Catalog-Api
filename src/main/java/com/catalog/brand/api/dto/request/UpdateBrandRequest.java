package com.catalog.brand.api.dto.request;

import jakarta.validation.constraints.*;

public record UpdateBrandRequest(

    @NotBlank(message = "Brand name is required")
    @Size(min = 1, max = 200)
    String name,

    @Size(max = 200)
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
             message = "Slug must be lowercase alphanumeric with hyphens only")
    String slug,

    @Size(max = 5000)
    String description,

    @Size(max = 1000)
    String logoUrl,

    @Size(max = 1000)
    String websiteUrl,

    @Size(max = 100)
    String countryOfOrigin,

    @Min(1800)
    Integer foundedYear,

    Boolean active,

    Boolean featured
) {}

