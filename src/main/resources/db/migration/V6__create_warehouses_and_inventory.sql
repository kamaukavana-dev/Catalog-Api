-- ============================================================
-- WAREHOUSES
-- ============================================================
CREATE TABLE warehouses (
    id               UUID            NOT NULL DEFAULT gen_random_uuid(),
    version          BIGINT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(100),
    updated_by       VARCHAR(100),
    deleted_at       TIMESTAMPTZ,

    code             VARCHAR(50)     NOT NULL,
    name             VARCHAR(200)    NOT NULL,
    type             VARCHAR(30)     NOT NULL,
    address_line1    VARCHAR(300),
    city             VARCHAR(100),
    country_code     CHAR(2),
    is_active        BOOLEAN         NOT NULL DEFAULT TRUE,

    CONSTRAINT warehouses_pkey PRIMARY KEY (id),
    CONSTRAINT warehouses_type_check CHECK (type IN ('MAIN', 'RETAIL', 'DARK_STORE', 'FULFILLMENT_CENTER', 'PICKUP_POINT')),
    CONSTRAINT warehouses_code_not_blank CHECK (TRIM(code) <> '')
);

CREATE UNIQUE INDEX uq_warehouses_code ON warehouses(code) WHERE deleted_at IS NULL;
CREATE INDEX idx_warehouses_active ON warehouses(is_active) WHERE deleted_at IS NULL;

-- ============================================================
-- INVENTORY
-- ============================================================
CREATE TABLE inventory (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    deleted_at        TIMESTAMPTZ,

    variant_id        UUID        NOT NULL,
    warehouse_id      UUID        NOT NULL,
    quantity          INTEGER     NOT NULL DEFAULT 0,
    reserved_quantity INTEGER     NOT NULL DEFAULT 0,
    reorder_level     INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT inventory_pkey PRIMARY KEY (id),
    CONSTRAINT inventory_variant_fk FOREIGN KEY (variant_id) REFERENCES variants(id),
    CONSTRAINT inventory_warehouse_fk FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT uq_inventory_variant_warehouse UNIQUE (variant_id, warehouse_id),
    CONSTRAINT inventory_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT inventory_reserved_non_negative CHECK (reserved_quantity >= 0),
    CONSTRAINT inventory_reserved_lte_quantity CHECK (reserved_quantity <= quantity),
    CONSTRAINT inventory_reorder_non_negative CHECK (reorder_level >= 0)
);

CREATE INDEX idx_inventory_variant_id ON inventory(variant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_inventory_warehouse_id ON inventory(warehouse_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_inventory_low_stock ON inventory(variant_id, warehouse_id) WHERE deleted_at IS NULL AND quantity - reserved_quantity <= reorder_level AND reorder_level > 0;

-- ============================================================
-- INVENTORY RESERVATIONS
-- ============================================================
CREATE TABLE inventory_reservations (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    deleted_at      TIMESTAMPTZ,

    inventory_id    UUID        NOT NULL,
    reference_id    UUID        NOT NULL,
    quantity        INTEGER     NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at      TIMESTAMPTZ NOT NULL,
    released_at     TIMESTAMPTZ,

    CONSTRAINT inventory_reservations_pkey PRIMARY KEY (id),
    CONSTRAINT ir_inventory_fk FOREIGN KEY (inventory_id) REFERENCES inventory(id),
    CONSTRAINT ir_status_check CHECK (status IN ('ACTIVE', 'COMPLETED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ir_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ir_released_at_consistency CHECK (
        (status = 'ACTIVE' AND released_at IS NULL)
        OR (status != 'ACTIVE' AND released_at IS NOT NULL)
    )
);

CREATE INDEX idx_ir_active_expired ON inventory_reservations(expires_at ASC) WHERE status = 'ACTIVE';
CREATE INDEX idx_ir_inventory_id ON inventory_reservations(inventory_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_ir_reference_id ON inventory_reservations(reference_id) WHERE status = 'ACTIVE';

COMMENT ON COLUMN inventory.version IS 'Optimistic lock column inherited from BaseEntity.';
COMMENT ON COLUMN inventory.reserved_quantity IS 'Units committed to active reservations. available_quantity = quantity - reserved_quantity.';
COMMENT ON COLUMN inventory_reservations.reference_id IS 'Caller-provided identifier: checkout session, order attempt.';
COMMENT ON COLUMN inventory_reservations.released_at IS 'Timestamp of completion, expiry, or cancellation. NULL only when ACTIVE.';

