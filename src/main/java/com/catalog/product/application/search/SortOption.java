package com.catalog.product.application.search;

public enum SortOption {
    NEWEST("psp.created_at DESC, psp.product_id DESC"),
    OLDEST("psp.created_at ASC, psp.product_id ASC"),
    NAME_ASC("psp.name ASC, psp.product_id ASC"),
    NAME_DESC("psp.name DESC, psp.product_id ASC"),
    PRICE_LOW("psp.min_effective_price ASC, psp.product_id ASC"),
    PRICE_HIGH("psp.min_effective_price DESC, psp.product_id ASC");

    private final String sql;

    SortOption(String sql) {
        this.sql = sql;
    }

    public String getSql() {
        return sql;
    }

    public boolean supportsCursorPagination() {
        return true;
    }
}

