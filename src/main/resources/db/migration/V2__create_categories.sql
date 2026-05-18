-- Phase 2: Categories table with hierarchical support
-- Supports tree structure via adjacency list + materialized path
CREATE TABLE categories (
    -- Base columns from BaseEntity
    id               UUID            NOT NULL DEFAULT gen_random_uuid(),
    version          BIGINT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    deleted_at       TIMESTAMPTZ,

    -- Category-specific columns
    name             VARCHAR(200)    NOT NULL,
    slug             VARCHAR(200)    NOT NULL,
    description      TEXT,
    parent_id        UUID,
    depth            INTEGER         NOT NULL DEFAULT 0,
    path             VARCHAR(2000)   NOT NULL,
    display_order    INTEGER         NOT NULL DEFAULT 0,
    is_active        BOOLEAN         NOT NULL DEFAULT TRUE,
    image_url        VARCHAR(1000),
    meta_title       VARCHAR(200),
    meta_description VARCHAR(500),

    CONSTRAINT categories_pkey
        PRIMARY KEY (id),

    CONSTRAINT categories_parent_fk
        FOREIGN KEY (parent_id)
        REFERENCES categories(id),

    CONSTRAINT categories_depth_check
        CHECK (depth >= 0 AND depth <= 10),

    CONSTRAINT categories_name_not_blank
        CHECK (TRIM(name) <> ''),

    CONSTRAINT categories_slug_format
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

-- PARTIAL unique index: slug must be unique among non-deleted categories only
CREATE UNIQUE INDEX uq_categories_slug
    ON categories(slug)
    WHERE deleted_at IS NULL;

-- Index for child lookups (most frequent category query pattern)
CREATE INDEX idx_categories_parent_id
    ON categories(parent_id)
    WHERE deleted_at IS NULL;

-- Index for subtree queries: path prefix scan
CREATE INDEX idx_categories_path
    ON categories(path)
    WHERE deleted_at IS NULL;

-- Composite index for ordered child listing (navigation menus)
CREATE INDEX idx_categories_parent_order
    ON categories(parent_id, display_order)
    WHERE deleted_at IS NULL;

-- Trigram index for name search (Phase 8 will use this)
CREATE INDEX idx_categories_name_trgm
    ON categories USING gin(name gin_trgm_ops);

-- Soft delete filtering
CREATE INDEX idx_categories_deleted_at
    ON categories(deleted_at)
    WHERE deleted_at IS NOT NULL;

COMMENT ON TABLE categories IS 'Product taxonomy. Supports hierarchical tree via adjacency list + materialized path.';
COMMENT ON COLUMN categories.path IS 'Materialized path of ancestor UUIDs. Format: /grandparent-id/parent-id/self-id. Enables O(1) subtree and ancestor queries.';
COMMENT ON COLUMN categories.depth IS 'Tree depth. Root = 0. Max = 10. Enforced by CHECK constraint and service layer.';

