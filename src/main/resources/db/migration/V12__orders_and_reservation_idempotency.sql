-- ============================================================
-- ORDERS
-- ============================================================
CREATE TABLE IF NOT EXISTS orders (
    id                UUID            NOT NULL DEFAULT gen_random_uuid(),
    version           BIGINT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    deleted_at        TIMESTAMPTZ,

    customer_id       UUID,
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    total_amount      NUMERIC(19,4)   NOT NULL,
    currency          VARCHAR(3)      NOT NULL DEFAULT 'USD',
    payment_reference VARCHAR(200),

    CONSTRAINT orders_pkey PRIMARY KEY (id),
    CONSTRAINT orders_status_check CHECK (status IN (
        'PENDING', 'PAID', 'CANCELLED', 'SHIPPED', 'DELIVERED', 'RETURNED'
    )),
    CONSTRAINT orders_total_amount_non_negative CHECK (total_amount >= 0),
    CONSTRAINT orders_currency_length CHECK (char_length(currency) = 3)
);

CREATE INDEX IF NOT EXISTS idx_orders_status_created
    ON orders(status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_orders_customer_created
    ON orders(customer_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- ============================================================
-- ORDER LINE ITEMS
-- ============================================================
CREATE TABLE IF NOT EXISTS order_line_items (
    id                UUID            NOT NULL DEFAULT gen_random_uuid(),
    version           BIGINT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    deleted_at        TIMESTAMPTZ,

    order_id          UUID            NOT NULL,
    variant_id        UUID            NOT NULL,
    quantity          INTEGER         NOT NULL,
    unit_price        NUMERIC(19,4)   NOT NULL,
    total_price       NUMERIC(19,4)   NOT NULL,

    CONSTRAINT order_line_items_pkey PRIMARY KEY (id),
    CONSTRAINT oli_order_fk FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT oli_quantity_positive CHECK (quantity > 0),
    CONSTRAINT oli_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT oli_total_price_non_negative CHECK (total_price >= 0)
);

CREATE INDEX IF NOT EXISTS idx_order_line_items_order
    ON order_line_items(order_id);

CREATE INDEX IF NOT EXISTS idx_order_line_items_variant
    ON order_line_items(variant_id);

-- ============================================================
-- RESERVATION IDEMPOTENCY
-- Prevent duplicate ACTIVE reservations for same inventory+reference.
-- ============================================================
CREATE UNIQUE INDEX IF NOT EXISTS uq_ir_active_inventory_reference
    ON inventory_reservations(inventory_id, reference_id)
    WHERE status = 'ACTIVE';
