package com.catalog.category.api.dto.response;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record CategoryTreeResponse(
    UUID id,
    String name,
    String slug,
    int depth,
    boolean active,
    int displayOrder,
    List<CategoryTreeResponse> children
) {
    public static CategoryTreeResponse of(UUID id, String name, String slug,
                                          int depth, boolean active, int displayOrder) {
        return new CategoryTreeResponse(id, name, slug, depth, active,
                                        displayOrder, new ArrayList<>());
    }
}

