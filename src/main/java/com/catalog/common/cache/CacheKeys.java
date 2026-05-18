package com.catalog.common.cache;

import java.util.UUID;

public final class CacheKeys {

    private CacheKeys() {
    }

    public static String categoryById(UUID id) {
        return "id:" + id;
    }

    public static String categoryBySlug(String slug) {
        return "slug:" + slug;
    }

    public static final String CATEGORY_TREE = "tree:all";

    public static String brandById(UUID id) {
        return "id:" + id;
    }

    public static String brandBySlug(String slug) {
        return "slug:" + slug;
    }

    public static String productById(UUID id) {
        return "id:" + id;
    }

    public static String productBySlug(String slug) {
        return "slug:" + slug;
    }

    public static final String PRODUCT_SEARCH_PREFIX = "product:search:";
    public static final String PRODUCT_SEARCH_PATTERN = "product:search:*";

    public static String productSearch(String hash) {
        return PRODUCT_SEARCH_PREFIX + hash;
    }

    public static final String ATTRIBUTE_TYPES_ALL = "all";

    public static String attributeTypeById(UUID id) {
        return "id:" + id;
    }
}

