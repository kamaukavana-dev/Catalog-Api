-- ============================================================
-- ENHANCE product_images
-- Phase 4 created a minimal product_images table.
-- Phase 10 completes it with storage and processing fields.
-- ============================================================

-- Drop the old 'url' column — replaced by storage_key + computed public URL
ALTER TABLE product_images DROP COLUMN url;

-- Add storage, processing, and audit columns
ALTER TABLE product_images
    ADD COLUMN version          BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN created_by       VARCHAR(100),
    ADD COLUMN updated_by       VARCHAR(100),
    ADD COLUMN deleted_at       TIMESTAMPTZ,
    ADD COLUMN storage_key      VARCHAR(500),
    ADD COLUMN content_type     VARCHAR(100),
    ADD COLUMN file_size_bytes  BIGINT,
    ADD COLUMN width_px         INTEGER,
    ADD COLUMN height_px        INTEGER,
    ADD COLUMN status           VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE product_images
    ADD CONSTRAINT product_images_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),

    ADD CONSTRAINT product_images_content_type_check
        CHECK (content_type IS NULL
               OR content_type IN ('image/jpeg', 'image/png', 'image/webp')),

    ADD CONSTRAINT product_images_size_positive
        CHECK (file_size_bytes IS NULL OR file_size_bytes > 0);

-- Only READY images participate in normal reads
CREATE INDEX idx_product_images_ready
    ON product_images(product_id, sort_order ASC)
    WHERE status = 'READY' AND deleted_at IS NULL;

-- Cleanup job query: find stale PENDING records
CREATE INDEX idx_product_images_pending
    ON product_images(created_at ASC)
    WHERE status = 'PENDING';

COMMENT ON COLUMN product_images.storage_key IS
    'S3/MinIO object key. Format: products/{productId}/images/{imageId}. Public URL computed from config.storage.base-url + key.';
COMMENT ON COLUMN product_images.status IS
    'PENDING: upload session created. PROCESSING: upload confirmed, extracting metadata. READY: available for delivery. FAILED: processing error.';

-- ============================================================
-- VARIANT IMAGES
-- Same pattern as product_images. Variant overrides product visuals.
-- ============================================================
CREATE TABLE variant_images (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    deleted_at       TIMESTAMPTZ,

    variant_id       UUID        NOT NULL,
    storage_key      VARCHAR(500),
    alt_text         VARCHAR(300),
    sort_order       INTEGER     NOT NULL DEFAULT 0,
    is_primary       BOOLEAN     NOT NULL DEFAULT FALSE,
    content_type     VARCHAR(100),
    file_size_bytes  BIGINT,
    width_px         INTEGER,
    height_px        INTEGER,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    CONSTRAINT variant_images_pkey
        PRIMARY KEY (id),

    CONSTRAINT variant_images_variant_fk
        FOREIGN KEY (variant_id)
        REFERENCES variants(id)
        ON DELETE CASCADE,

    CONSTRAINT variant_images_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),

    CONSTRAINT variant_images_content_type_check
        CHECK (content_type IS NULL
               OR content_type IN ('image/jpeg', 'image/png', 'image/webp'))
);

-- Enforce single primary image per variant
CREATE UNIQUE INDEX uq_variant_images_primary
    ON variant_images(variant_id)
    WHERE is_primary = TRUE AND deleted_at IS NULL;

CREATE INDEX idx_variant_images_ready
    ON variant_images(variant_id, sort_order ASC)
    WHERE status = 'READY' AND deleted_at IS NULL;

COMMENT ON TABLE variant_images IS
    'Variant-level images. Override product images when present. Storefront: use variant images if available, fall back to product images.';

