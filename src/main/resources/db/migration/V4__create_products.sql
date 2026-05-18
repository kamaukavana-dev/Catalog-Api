-- Phase 4: Products
CREATE TABLE products (
    -- BaseEntity
    id                   UUID            NOT NULL DEFAULT gen_random_uuid(),
    version              BIGINT          NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(100),
    updated_by           VARCHAR(100),
    deleted_at           TIMESTAMPTZ,

    -- Identity
    name                 VARCHAR(300)    NOT NULL,
    slug                 VARCHAR(300)    NOT NULL,

    -- Content
    short_description    VARCHAR(1000),
    description          TEXT,

    -- Lifecycle
    status               VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',

    -- Relationships
    primary_category_id  UUID,
    brand_id             UUID,

    -- SEO
    meta_title           VARCHAR(200),
    meta_description     VARCHAR(500),

    CONSTRAINT products_pkey
        PRIMARY KEY (id),

    CONSTRAINT products_primary_category_fk
        FOREIGN KEY (primary_category_id)
        REFERENCES categories(id),

    CONSTRAINT products_brand_fk
        FOREIGN KEY (brand_id)
        REFERENCES brands(id),

    CONSTRAINT products_status_valid
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')),

    CONSTRAINT products_name_not_blank
        CHECK (TRIM(name) <> '')
);

CREATE TABLE product_categories (
    product_id           UUID            NOT NULL,
    category_id          UUID            NOT NULL,
    display_order        INTEGER         NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT product_categories_pkey
        PRIMARY KEY (product_id, category_id),

    CONSTRAINT product_categories_product_fk
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE CASCADE,

    CONSTRAINT product_categories_category_fk
        FOREIGN KEY (category_id)
        REFERENCES categories(id)
);

CREATE TABLE product_images (
    id                   UUID            NOT NULL DEFAULT gen_random_uuid(),
    product_id           UUID            NOT NULL,
    url                  VARCHAR(1000)   NOT NULL,
    alt_text             VARCHAR(300),
    sort_order           INTEGER         NOT NULL DEFAULT 0,
    is_primary           BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT product_images_pkey
        PRIMARY KEY (id),

    CONSTRAINT product_images_product_fk
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_products_slug
    ON products(slug)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_products_status
    ON products(status)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_products_primary_category
    ON products(primary_category_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_products_brand
    ON products(brand_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_products_status_created_at
    ON products(status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_products_name_trgm
    ON products USING gin(name gin_trgm_ops);

CREATE INDEX idx_products_deleted_at
    ON products(deleted_at)
    WHERE deleted_at IS NOT NULL;

CREATE INDEX idx_product_categories_category_id
    ON product_categories(category_id);

CREATE UNIQUE INDEX uq_product_images_primary
    ON product_images(product_id)
    WHERE is_primary = TRUE;

CREATE INDEX idx_product_images_product_order
    ON product_images(product_id, sort_order ASC);

COMMENT ON TABLE products IS 'Core product catalog. Variant is the sellable unit. Product is the conceptual grouping.';
COMMENT ON COLUMN products.status IS 'Lifecycle state. Transitions enforced in service layer. ARCHIVED is terminal.';
COMMENT ON COLUMN products.primary_category_id IS 'Canonical SEO category. Required for ACTIVE status. Nullable for DRAFT.';
COMMENT ON TABLE product_categories IS 'Secondary category assignments for merchandising and discoverability.';
COMMENT ON TABLE product_images IS 'Product-level hero images. Variant-level images in variant_images (Phase 5).';

