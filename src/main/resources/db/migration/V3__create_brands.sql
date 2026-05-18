-- Phase 3: Brands table
-- Global reusable brand catalog. No hierarchy (unlike categories).
-- Foreign key to products: Phase 4.
CREATE TABLE brands (
    -- BaseEntity columns
    id               UUID            NOT NULL DEFAULT gen_random_uuid(),
    version          BIGINT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    deleted_at       TIMESTAMPTZ,

    -- Core identity
    name             VARCHAR(200)    NOT NULL,
    slug             VARCHAR(200)    NOT NULL,
    description      TEXT,

    -- Media
    logo_url         VARCHAR(1000),
    website_url      VARCHAR(1000),

    -- Business metadata
    country_of_origin VARCHAR(100),
    founded_year     SMALLINT,

    -- Operational flags
    is_active        BOOLEAN         NOT NULL DEFAULT TRUE,
    is_featured      BOOLEAN         NOT NULL DEFAULT FALSE,

    CONSTRAINT brands_pkey
        PRIMARY KEY (id),

    CONSTRAINT brands_founded_year_range
        CHECK (founded_year IS NULL OR (founded_year >= 1800 AND founded_year <= 2100)),

    CONSTRAINT brands_name_not_blank
        CHECK (TRIM(name) <> ''),

    CONSTRAINT brands_slug_format
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

-- Partial unique: slug unique among non-deleted brands
CREATE UNIQUE INDEX uq_brands_slug
    ON brands(slug)
    WHERE deleted_at IS NULL;

-- Partial unique: name unique among non-deleted brands (case-insensitive)
CREATE UNIQUE INDEX uq_brands_name
    ON brands(LOWER(name))
    WHERE deleted_at IS NULL;

-- Trigram index for name search (ILIKE queries)
CREATE INDEX idx_brands_name_trgm
    ON brands USING gin(name gin_trgm_ops);

-- Featured brands filter (small, fast lookup for storefronts)
CREATE INDEX idx_brands_featured_active
    ON brands(is_featured, is_active)
    WHERE deleted_at IS NULL;

-- Country of origin filter
CREATE INDEX idx_brands_country
    ON brands(country_of_origin)
    WHERE deleted_at IS NULL AND country_of_origin IS NOT NULL;

-- Soft delete filter
CREATE INDEX idx_brands_deleted_at
    ON brands(deleted_at)
    WHERE deleted_at IS NOT NULL;

COMMENT ON TABLE brands IS 'Brand catalog. Optional relationship to products. Globally reusable across categories.';
COMMENT ON COLUMN brands.founded_year IS 'Year brand was established. SMALLINT: range 1800-2100. Nullable.';
COMMENT ON COLUMN brands.is_featured IS 'Featured brands appear in storefront highlights. Managed by admin.';

