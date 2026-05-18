package com.catalog.product.application.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maintains the product_search_projection denormalized read model.
 *
 * Update triggers:
 * 1. ProductMutatedEvent → refresh product-level fields
 * 2. VariantStatusChangedEvent → recalculate variant counts and prices
 * 3. InventoryUpdatedEvent (new) → recalculate in_stock status
 * 4. Scheduled catch-up: finds projections older than 10 minutes and rebuilds
 *
 * All operations are UPSERT (INSERT ON CONFLICT DO UPDATE).
 * Thread-safe: projection is keyed by product_id (UUID) with idempotent updates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchProjectionService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * Full projection rebuild for a single product.
     * Computes all fields in one SQL statement and upserts.
     * Designed to be idempotent: safe to call multiple times for the same product.
     */
    @Transactional
    public void refreshProjection(UUID productId) {
        log.debug("Refreshing search projection for product id={}", productId);

        String sql = """
            INSERT INTO product_search_projection (
                product_id, name, slug, short_description, status, deleted_at, created_at,
                primary_category_id, primary_category_name,
                brand_id, brand_name,
                min_effective_price, max_base_price,
                active_variant_count, in_stock_variant_count, in_stock,
                searchable_text, projection_updated_at
            )
            SELECT
                p.id,
                p.name,
                p.slug,
                p.short_description,
                p.status::varchar,
                p.deleted_at,
                p.created_at,
                p.primary_category_id,
                c.name,
                p.brand_id,
                b.name,
                vps.min_price,
                vps.max_price,
                COALESCE(vps.active_count, 0),
                COALESCE(vps.in_stock_count, 0),
                COALESCE(vps.in_stock_count, 0) > 0,
                to_tsvector('english',
                    COALESCE(p.name, '') || ' ' ||
                    COALESCE(p.short_description, '') || ' ' ||
                    COALESCE(b.name, '') || ' ' ||
                    COALESCE(c.name, '')
                ),
                NOW()
            FROM products p
            LEFT JOIN categories c ON c.id = p.primary_category_id AND c.deleted_at IS NULL
            LEFT JOIN brands     b ON b.id = p.brand_id             AND b.deleted_at IS NULL
            LEFT JOIN (
                SELECT
                    v.product_id,
                    MIN(
                        CASE WHEN v.sale_price IS NOT NULL
                                  AND (v.sale_start_at IS NULL OR v.sale_start_at <= NOW())
                                  AND (v.sale_end_at   IS NULL OR v.sale_end_at   >  NOW())
                             THEN v.sale_price ELSE v.base_price END
                    ) AS min_price,
                    MAX(v.base_price) AS max_price,
                    COUNT(*) AS active_count,
                    SUM(CASE WHEN COALESCE(inv.total_available, 0) > 0
                             THEN 1 ELSE 0 END) AS in_stock_count
                FROM variants v
                LEFT JOIN (
                    SELECT i.variant_id,
                           SUM(i.quantity - i.reserved_quantity) AS total_available
                    FROM inventory i
                    WHERE i.deleted_at IS NULL
                    GROUP BY i.variant_id
                ) inv ON inv.variant_id = v.id
                WHERE v.deleted_at IS NULL AND v.status = 'ACTIVE' AND v.product_id = :productId
                GROUP BY v.product_id
            ) vps ON vps.product_id = p.id
            WHERE p.id = :productId
            ON CONFLICT (product_id) DO UPDATE SET
                name                    = EXCLUDED.name,
                slug                    = EXCLUDED.slug,
                short_description       = EXCLUDED.short_description,
                status                  = EXCLUDED.status,
                deleted_at              = EXCLUDED.deleted_at,
                primary_category_id     = EXCLUDED.primary_category_id,
                primary_category_name   = EXCLUDED.primary_category_name,
                brand_id                = EXCLUDED.brand_id,
                brand_name              = EXCLUDED.brand_name,
                min_effective_price     = EXCLUDED.min_effective_price,
                max_base_price          = EXCLUDED.max_base_price,
                active_variant_count    = EXCLUDED.active_variant_count,
                in_stock_variant_count  = EXCLUDED.in_stock_variant_count,
                in_stock                = EXCLUDED.in_stock,
                searchable_text         = EXCLUDED.searchable_text,
                projection_updated_at   = NOW()
            """;

        int rows = jdbcTemplate.update(sql, new MapSqlParameterSource("productId", productId));
        log.debug("Projection upserted for product id={}: {} row(s)", productId, rows);
    }

    // -------------------------------------------------------------------------
    // Event listeners — fire AFTER COMMIT to guarantee DB-consistent reads
    // -------------------------------------------------------------------------

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductMutated(com.catalog.product.event.ProductMutatedEvent event) {
        refreshProjection(event.id());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVariantMutated(com.catalog.variant.event.VariantMutatedEvent event) {
        refreshProjection(event.productId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInventoryUpdated(com.catalog.inventory.event.InventoryUpdatedEvent event) {
        refreshProjection(event.productId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBrandMutated(com.catalog.brand.event.BrandMutatedEvent event) {
        refreshByRelatedProducts("brand", event.id(), """
                SELECT id FROM products
                WHERE deleted_at IS NULL AND brand_id = :id
                LIMIT 2000
                """);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCategoryMutated(com.catalog.category.event.CategoryMutatedEvent event) {
        refreshByRelatedProducts("category", event.id(), """
                SELECT id FROM products
                WHERE deleted_at IS NULL AND primary_category_id = :id
                LIMIT 2000
                """);
    }

    /**
     * Safety net: rebuild any projections that haven't been refreshed recently.
     * Catches missed events (app restart during event processing, async errors).
     * Runs every 10 minutes. Does NOT replace event-driven updates.
     */
    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void catchUpStaleProjections() {
        List<UUID> staleIds = findProductsUpdatedAfterProjection();

        if (staleIds.isEmpty()) return;

        log.info("Catch-up: rebuilding {} stale search projections", staleIds.size());
        staleIds.forEach(id -> {
            try { refreshProjection(id); }
            catch (Exception e) {
                log.error("Catch-up failed for product id={}: {}", id, e.getMessage());
            }
        });
    }

    private List<UUID> findProductsUpdatedAfterProjection() {
        String sql = """
            SELECT p.id FROM products p
            LEFT JOIN product_search_projection psp ON psp.product_id = p.id
            WHERE p.deleted_at IS NULL
              AND (psp.product_id IS NULL
                   OR p.updated_at > psp.projection_updated_at)
            LIMIT 500
            """;
        return jdbcTemplate.queryForList(sql, Map.of(), UUID.class);
    }

    /**
     * Backfill: rebuilds all projections from scratch.
     * Used after initial deployment or after structural migrations.
     * Run once manually: POST /api/v1/admin/search-projection/rebuild
     */
    @Transactional
    public int rebuildAll() {
        log.warn("FULL PROJECTION REBUILD INITIATED. This will take time on large catalogs.");
        List<UUID> allProductIds = jdbcTemplate.queryForList(
            "SELECT id FROM products WHERE deleted_at IS NULL",
            Map.of(), UUID.class);

        int count = 0;
        for (UUID id : allProductIds) {
            try { refreshProjection(id); count++; }
            catch (Exception e) { log.error("Rebuild failed for id={}", id, e); }
        }
        log.info("Full rebuild complete: {}/{} projections updated", count, allProductIds.size());
        return count;
    }

    private void refreshByRelatedProducts(String relation, UUID relatedId, String sql) {
        List<UUID> productIds = jdbcTemplate.queryForList(
                sql,
                new MapSqlParameterSource("id", relatedId),
                UUID.class
        );

        if (productIds.isEmpty()) {
            return;
        }

        log.info("Refreshing {} search projections due to {} mutation id={}",
                productIds.size(), relation, relatedId);
        for (UUID productId : productIds) {
            try {
                refreshProjection(productId);
            } catch (Exception e) {
                log.error("Projection refresh failed after {} mutation: relatedId={} productId={} error={}",
                        relation, relatedId, productId, e.getMessage(), e);
            }
        }
    }
}
