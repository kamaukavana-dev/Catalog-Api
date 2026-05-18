-- ============================================================
-- DENORMALIZED SEARCH PROJECTION
-- Write-time aggregation. Read-time simplicity.
-- Updated asynchronously via domain events.
-- Replaces the live-aggregate CTE in ProductSearchService.
-- ============================================================
CREATE TABLE product_search_projection (
    product_id              UUID            NOT NULL,
    name                    VARCHAR(300)    NOT NULL,
    slug                    VARCHAR(300)    NOT NULL,
    short_description       VARCHAR(1000),
    status                  VARCHAR(20)     NOT NULL,
    deleted_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL,

    -- Denormalized relationships (avoids JOIN on every search)
    primary_category_id     UUID,
    primary_category_name   VARCHAR(200),
    brand_id                UUID,
    brand_name              VARCHAR(200),

    -- Pre-computed variant price aggregates
    min_effective_price     NUMERIC(19,4),
    max_base_price          NUMERIC(19,4),
    active_variant_count    INTEGER         NOT NULL DEFAULT 0,
    in_stock_variant_count  INTEGER         NOT NULL DEFAULT 0,
    in_stock                BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Full-text search vector: combines all searchable text.
    -- Updated on every projection refresh via to_tsvector().
    -- GIN index enables O(log n) full-text lookup vs O(n) ILIKE scan.
    searchable_text         TSVECTOR,

    projection_updated_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT psp_pkey PRIMARY KEY (product_id)
);

-- Full-text search: the Phase 13 upgrade from ILIKE
CREATE INDEX idx_psp_searchable_text
    ON product_search_projection USING gin(searchable_text);

-- Trigram fallback for partial/fuzzy name matches
CREATE INDEX idx_psp_name_trgm
    ON product_search_projection USING gin(name gin_trgm_ops);

-- Storefront primary query pattern: ACTIVE + price range
CREATE INDEX idx_psp_status_price
    ON product_search_projection(status, min_effective_price ASC)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

-- Category browsing
CREATE INDEX idx_psp_category
    ON product_search_projection(primary_category_id)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

-- Brand filtering
CREATE INDEX idx_psp_brand
    ON product_search_projection(brand_id)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

-- Cursor sort: newest first (most common storefront sort)
CREATE INDEX idx_psp_created_at_cursor
    ON product_search_projection(created_at DESC, product_id DESC)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

-- Stale projection detection for catch-up job
CREATE INDEX idx_psp_projection_updated
    ON product_search_projection(projection_updated_at ASC);

COMMENT ON TABLE product_search_projection IS
    'Denormalized read model for product search. '
    'Updated asynchronously on product/variant/inventory mutations. '
    'Trades write complexity for read simplicity. '
    'Never query products + variants + inventory live in the search path.';

COMMENT ON COLUMN product_search_projection.searchable_text IS
    'Combined tsvector of name, short_description, brand_name, category_name. '
    'Enables PostgreSQL native full-text search. Replaces ILIKE.';

-- ============================================================
-- pg_stat_statements: query performance visibility
-- Requires PostgreSQL to be started with:
--   shared_preload_libraries = 'pg_stat_statements'
-- In docker-compose: command: postgres -c shared_preload_libraries=pg_stat_statements
-- ============================================================
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

COMMENT ON EXTENSION pg_stat_statements IS
    'Tracks per-query execution statistics. '
    'Query: SELECT query, calls, total_exec_time/calls AS avg_ms, rows '
    'FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 20;';

