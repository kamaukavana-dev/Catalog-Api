package com.catalog.product.application.search;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 13 rewrite: Queries product_search_projection instead of live aggregation.
 *
 * The old variant_price_stats CTE is eliminated entirely from hot-path search.
 * The projection table has pre-computed all aggregate values at write time.
 *
 * The conditional CTE flag (needsVariantStats) is retained for the rare
 * admin/debug queries that need live data. Standard storefront queries never hit it.
 */
public class ProductSearchQueryBuilder {

    /**
     * Base SQL now reads the denormalized projection.
     * No joins to variants, inventory, or live aggregates.
     * One table + two small LEFT JOINs (categories, brands already denormalized
     * but kept as cross-checks for soft-delete accuracy).
     */
    private static final String BASE_SQL = """
            SELECT
                psp.product_id         AS id,
                psp.name,
                psp.slug,
                psp.short_description,
                psp.status,
                psp.primary_category_id,
                psp.primary_category_name AS category_name,
                psp.brand_id,
                psp.brand_name,
                psp.min_effective_price,
                psp.max_base_price,
                psp.in_stock,
                psp.active_variant_count AS variant_count,
                psp.created_at
            FROM product_search_projection psp
            WHERE psp.status = 'ACTIVE'
              AND psp.deleted_at IS NULL
            """;

    private final StringBuilder conditions = new StringBuilder();
    private final MapSqlParameterSource params = new MapSqlParameterSource();
    private String orderBy = SortOption.NEWEST.getSql();
    private int limit = 21;

    // -------------------------------------------------------------------------
    // Phase 13: Full-text search replaces ILIKE.
    // ts_query(search) uses the tsvector GIN index → O(log n) lookup.
    // -------------------------------------------------------------------------
    public ProductSearchQueryBuilder textSearch(String search) {
        if (search == null || search.isBlank()) return this;
        String trimmed = search.trim();

        // Short queries (<=3 chars) are not reliably handled by FTS stemming and can produce
        // inconsistent trigram similarity results. Use a deterministic prefix match instead.
        if (trimmed.length() <= 3) {
            conditions.append("AND psp.name ILIKE :searchPrefix\n");
            params.addValue("searchPrefix", trimmed + "%");
            return this;
        }

        // Use plainto_tsquery: handles multi-word queries without special chars.
        // Fallback to pg_trgm similarity for partial/fuzzy matches.
        conditions.append("""
            AND (
                psp.searchable_text @@ plainto_tsquery('english', :searchQuery)
                OR psp.name % :searchFuzzy
            )
            """);
        params.addValue("searchQuery", trimmed);
        params.addValue("searchFuzzy", trimmed);
        return this;
    }

    public ProductSearchQueryBuilder categoryFilter(UUID categoryId, String categoryPath) {
        if (categoryId == null) return this;
        // Reuse the materialized path approach from Phase 2 — still valid
        conditions.append("""
            AND EXISTS (
                SELECT 1 FROM categories cat
                WHERE cat.id = psp.primary_category_id
                  AND cat.path LIKE :categoryPath
                  AND cat.deleted_at IS NULL
            )
            """);
        params.addValue("categoryPath", categoryPath + "%");
        return this;
    }

    public ProductSearchQueryBuilder brandFilter(UUID brandId) {
        if (brandId == null) return this;
        conditions.append("AND psp.brand_id = :brandId\n");
        params.addValue("brandId", brandId);
        return this;
    }

    public ProductSearchQueryBuilder priceFilter(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null) {
            conditions.append("AND psp.min_effective_price >= :minPrice\n");
            params.addValue("minPrice", minPrice);
        }
        if (maxPrice != null) {
            conditions.append("AND psp.min_effective_price <= :maxPrice\n");
            params.addValue("maxPrice", maxPrice);
        }
        return this;
    }

    public ProductSearchQueryBuilder inStockFilter(Boolean inStock) {
        if (Boolean.TRUE.equals(inStock)) {
            conditions.append("AND psp.in_stock = TRUE\n");
        }
        return this;
    }

    /**
     * Attribute filter still requires a subquery join to variant_attribute_values.
     * This is the one remaining live-join in the search path.
     * Acceptable: attribute filters are applied to a pre-narrowed result set.
     * Future Phase: materialize matched_attribute_values into the projection as JSONB.
     */
    public ProductSearchQueryBuilder attributeFilter(Set<UUID> attributeValueIds,
                                                      int numAttributeTypes) {
        if (attributeValueIds == null || attributeValueIds.isEmpty()) return this;
        conditions.append("""
            AND EXISTS (
                SELECT 1
                FROM variants v_attr
                INNER JOIN variant_attribute_values vav ON vav.variant_id = v_attr.id
                INNER JOIN attribute_values av ON av.id = vav.attribute_value_id
                WHERE v_attr.product_id = psp.product_id
                  AND v_attr.deleted_at IS NULL AND v_attr.status = 'ACTIVE'
                  AND vav.attribute_value_id IN (:attrValueIds)
                GROUP BY v_attr.id
                HAVING COUNT(DISTINCT av.attribute_type_id) = :numAttrTypes
            )
            """);
        params.addValue("attrValueIds", attributeValueIds);
        params.addValue("numAttrTypes", numAttributeTypes);
        return this;
    }

    public ProductSearchQueryBuilder cursorCondition(SearchCursor cursor, SortOption sort) {
        if (cursor == null) return this;
        switch (sort) {
            case NEWEST -> {
                if (cursor.createdAt() == null) return this;
                conditions.append("""
                    AND (psp.created_at < :cursorTs
                         OR (psp.created_at = :cursorTs AND psp.product_id < :cursorId::uuid))
                    """);
                params.addValue("cursorTs", Timestamp.from(cursor.createdAt()));
                params.addValue("cursorId", cursor.id().toString());
            }
            case OLDEST -> {
                if (cursor.createdAt() == null) return this;
                conditions.append("""
                    AND (psp.created_at > :cursorTs
                         OR (psp.created_at = :cursorTs AND psp.product_id > :cursorId::uuid))
                    """);
                params.addValue("cursorTs", Timestamp.from(cursor.createdAt()));
                params.addValue("cursorId", cursor.id().toString());
            }
            case NAME_ASC -> {
                conditions.append("""
                    AND (psp.name > :cursorName
                         OR (psp.name = :cursorName AND psp.product_id > :cursorId::uuid))
                    """);
                params.addValue("cursorName", cursor.name());
                params.addValue("cursorId", cursor.id().toString());
            }
            case NAME_DESC -> {
                conditions.append("""
                    AND (psp.name < :cursorName
                         OR (psp.name = :cursorName AND psp.product_id > :cursorId::uuid))
                    """);
                params.addValue("cursorName", cursor.name());
                params.addValue("cursorId", cursor.id().toString());
            }
            case PRICE_LOW -> {
                if (cursor.price() == null) return this;
                conditions.append("""
                    AND (psp.min_effective_price > :cursorPrice
                         OR (psp.min_effective_price = :cursorPrice
                             AND psp.product_id > :cursorId::uuid))
                    """);
                params.addValue("cursorPrice", cursor.price());
                params.addValue("cursorId", cursor.id().toString());
            }
            case PRICE_HIGH -> {
                if (cursor.price() == null) return this;
                conditions.append("""
                    AND (psp.min_effective_price < :cursorPrice
                         OR (psp.min_effective_price = :cursorPrice
                              AND psp.product_id > :cursorId::uuid))
                    """);
                params.addValue("cursorPrice", cursor.price());
                params.addValue("cursorId", cursor.id().toString());
            }
        }
        return this;
    }

    public ProductSearchQueryBuilder sort(SortOption sort) {
        this.orderBy = sort.getSql();
        return this;
    }

    public ProductSearchQueryBuilder limit(int pageSize) {
        this.limit = pageSize + 1;
        return this;
    }

    public record SearchSql(String sql, MapSqlParameterSource params) {}

    public SearchSql build() {
        String sql = BASE_SQL + conditions + "ORDER BY " + orderBy + "\nLIMIT " + limit;
        return new SearchSql(sql, params);
    }
}
