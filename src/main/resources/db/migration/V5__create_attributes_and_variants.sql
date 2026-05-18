-- ============================================================
-- ATTRIBUTE TYPES
-- ============================================================
CREATE TABLE attribute_types (
    id               UUID            NOT NULL DEFAULT gen_random_uuid(),
    version          BIGINT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    deleted_at       TIMESTAMPTZ,

    name             VARCHAR(100)    NOT NULL,
    display_name     VARCHAR(100)    NOT NULL,
    data_type        VARCHAR(20)     NOT NULL,
    unit             VARCHAR(50),
    is_filterable    BOOLEAN         NOT NULL DEFAULT TRUE,
    display_order    INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT attribute_types_pkey
        PRIMARY KEY (id),

    CONSTRAINT attribute_types_data_type_check
        CHECK (data_type IN ('TEXT', 'NUMBER', 'COLOR', 'BOOLEAN')),

    CONSTRAINT attribute_types_name_not_blank
        CHECK (TRIM(name) <> '')
);

CREATE UNIQUE INDEX uq_attribute_types_name
    ON attribute_types(LOWER(name))
    WHERE deleted_at IS NULL;

CREATE INDEX idx_attribute_types_display_order
    ON attribute_types(display_order)
    WHERE deleted_at IS NULL;

-- ============================================================
-- ATTRIBUTE VALUES
-- ============================================================
CREATE TABLE attribute_values (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    deleted_at          TIMESTAMPTZ,

    attribute_type_id   UUID            NOT NULL,
    value               VARCHAR(200)    NOT NULL,
    display_value       VARCHAR(200)    NOT NULL,
    display_order       INTEGER         NOT NULL DEFAULT 0,
    hex_code            VARCHAR(7),

    CONSTRAINT attribute_values_pkey
        PRIMARY KEY (id),

    CONSTRAINT attribute_values_type_fk
        FOREIGN KEY (attribute_type_id)
        REFERENCES attribute_types(id),

    CONSTRAINT attribute_values_value_not_blank
        CHECK (TRIM(value) <> ''),

    CONSTRAINT attribute_values_hex_format
        CHECK (hex_code IS NULL OR hex_code ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE UNIQUE INDEX uq_attribute_values_type_value
    ON attribute_values(attribute_type_id, LOWER(value))
    WHERE deleted_at IS NULL;

CREATE INDEX idx_attribute_values_type_order
    ON attribute_values(attribute_type_id, display_order ASC)
    WHERE deleted_at IS NULL;

-- ============================================================
-- VARIANTS
-- ============================================================
CREATE TABLE variants (
    id               UUID            NOT NULL DEFAULT gen_random_uuid(),
    version          BIGINT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    deleted_at       TIMESTAMPTZ,

    product_id       UUID            NOT NULL,
    internal_sku     VARCHAR(50)     NOT NULL,
    merchant_sku     VARCHAR(200),

    status           VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',

    base_price       NUMERIC(19,4)   NOT NULL,
    sale_price       NUMERIC(19,4),
    sale_start_at    TIMESTAMPTZ,
    sale_end_at      TIMESTAMPTZ,
    cost_price       NUMERIC(19,4),

    tax_class        VARCHAR(20)     NOT NULL DEFAULT 'STANDARD',

    weight_grams     INTEGER,
    length_mm        INTEGER,
    width_mm         INTEGER,
    height_mm        INTEGER,

    CONSTRAINT variants_pkey
        PRIMARY KEY (id),

    CONSTRAINT variants_product_fk
        FOREIGN KEY (product_id)
        REFERENCES products(id),

    CONSTRAINT variants_status_check
        CHECK (status IN ('DRAFT', 'ACTIVE', 'DISCONTINUED', 'ARCHIVED')),

    CONSTRAINT variants_tax_class_check
        CHECK (tax_class IN ('STANDARD', 'REDUCED', 'ZERO', 'EXEMPT')),

    CONSTRAINT variants_base_price_positive
        CHECK (base_price > 0),

    CONSTRAINT variants_sale_price_positive
        CHECK (sale_price IS NULL OR sale_price > 0),

    CONSTRAINT variants_cost_price_positive
        CHECK (cost_price IS NULL OR cost_price > 0),

    CONSTRAINT variants_sale_date_range
        CHECK (
            sale_start_at IS NULL
            OR sale_end_at IS NULL
            OR sale_start_at < sale_end_at
        ),

    CONSTRAINT variants_sale_price_requires_bounds
        CHECK (
            sale_price IS NULL
            OR sale_start_at IS NOT NULL
            OR sale_end_at IS NOT NULL
        )
);

CREATE UNIQUE INDEX uq_variants_internal_sku
    ON variants(internal_sku);

CREATE UNIQUE INDEX uq_variants_merchant_sku
    ON variants(merchant_sku)
    WHERE merchant_sku IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_variants_product_id
    ON variants(product_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_variants_product_status
    ON variants(product_id, status)
    WHERE deleted_at IS NULL;

-- ============================================================
-- VARIANT ATTRIBUTE VALUES
-- ============================================================
CREATE TABLE variant_attribute_values (
    variant_id          UUID    NOT NULL,
    attribute_value_id  UUID    NOT NULL,

    CONSTRAINT variant_attribute_values_pkey
        PRIMARY KEY (variant_id, attribute_value_id),

    CONSTRAINT vav_variant_fk
        FOREIGN KEY (variant_id)
        REFERENCES variants(id)
        ON DELETE CASCADE,

    CONSTRAINT vav_attribute_value_fk
        FOREIGN KEY (attribute_value_id)
        REFERENCES attribute_values(id)
);

CREATE INDEX idx_variant_attribute_values_value
    ON variant_attribute_values(attribute_value_id);

COMMENT ON TABLE attribute_types IS 'Defines a dimension of product variation (Color, Size, RAM). Not product-specific.';
COMMENT ON TABLE attribute_values IS 'Possible values for an attribute type (Blue, Medium, 16GB). Reusable across products.';
COMMENT ON TABLE variants IS 'The sellable unit. Every purchase, inventory record, and SKU references a variant, not a product.';
COMMENT ON COLUMN variants.internal_sku IS 'System-generated. Globally unique. Immutable. Primary operational identifier for warehouse, logistics, and ERP.';
COMMENT ON COLUMN variants.merchant_sku IS 'Merchant-provided. Optional. For external system integration. Not globally unique - prepare for per-merchant scoping in multi-tenant future.';
COMMENT ON COLUMN variants.base_price IS 'NUMERIC(19,4). Never FLOAT. Canonical selling price before discounts.';
COMMENT ON COLUMN variants.sale_price IS 'Promotional price. Requires at least one time bound. Effective only within sale_start_at and sale_end_at window.';

