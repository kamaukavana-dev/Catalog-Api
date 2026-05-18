-- ============================================================
-- HARDENING FIXES
-- - Ensure pg_trgm extension for trigram indexes
-- - Replace inventory unique constraint with partial unique index
-- ============================================================

-- Required for gin_trgm_ops index usage in V9
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- If V9 failed to create the index due to missing extension, ensure it's present now
CREATE INDEX IF NOT EXISTS idx_psp_name_trgm
    ON product_search_projection USING gin(name gin_trgm_ops);

-- Replace strict unique constraint with partial unique index
-- to support soft-deleted rows.
ALTER TABLE inventory
    DROP CONSTRAINT IF EXISTS uq_inventory_variant_warehouse;

CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_variant_warehouse_active
    ON inventory(variant_id, warehouse_id)
    WHERE deleted_at IS NULL;

