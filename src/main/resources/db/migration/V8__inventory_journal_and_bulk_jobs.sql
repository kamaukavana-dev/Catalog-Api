-- ============================================================
-- INVENTORY JOURNAL
-- Append-only. Every mutation recorded immutably.
-- ============================================================
CREATE TABLE inventory_journal (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Denormalized for query performance — journal must be
    -- queryable even if inventory/variant/warehouse are soft-deleted
    inventory_id        UUID        NOT NULL,
    variant_id          UUID        NOT NULL,
    warehouse_id        UUID        NOT NULL,

    operation_type      VARCHAR(30) NOT NULL,

    -- Physical quantity change
    quantity_before     INTEGER     NOT NULL,
    quantity_after      INTEGER     NOT NULL,
    quantity_delta      INTEGER     NOT NULL,   -- = after - before. Redundant by design.

    -- Reserved quantity change
    reserved_before     INTEGER     NOT NULL,
    reserved_after      INTEGER     NOT NULL,
    reserved_delta      INTEGER     NOT NULL,

    -- What triggered this operation
    reference_type      VARCHAR(50),     -- RESERVATION, ORDER, TRANSFER, BULK_IMPORT, etc.
    reference_id        UUID,            -- FK to the relevant entity (reservation ID, etc.)

    -- Who triggered it
    actor_type          VARCHAR(30) NOT NULL,
    actor_id            VARCHAR(200),    -- User ID or system identifier

    -- Human-readable reason for admin/audit
    reason              TEXT,

    CONSTRAINT inventory_journal_pkey
        PRIMARY KEY (id),

    CONSTRAINT ij_operation_type_check
        CHECK (operation_type IN (
            'RECEIVE', 'SALE', 'RESERVATION_CREATE', 'RESERVATION_RELEASE',
            'RECONCILIATION', 'TRANSFER_OUT', 'TRANSFER_IN', 'RETURN',
            'DAMAGE', 'MANUAL_ADJUSTMENT', 'INITIAL_STOCK'
        )),

    CONSTRAINT ij_actor_type_check
        CHECK (actor_type IN (
            'SYSTEM', 'ADMIN_USER', 'SCHEDULED_JOB',
            'ERP_INTEGRATION', 'API_CLIENT'
        )),

    -- Tamper-detection: delta must match before/after arithmetic
    CONSTRAINT ij_quantity_delta_consistency
        CHECK (quantity_delta = quantity_after - quantity_before),

    CONSTRAINT ij_reserved_delta_consistency
        CHECK (reserved_delta = reserved_after - reserved_before)
);

-- Primary audit query: full history of an inventory record
CREATE INDEX idx_ij_inventory_id
    ON inventory_journal(inventory_id, created_at DESC);

-- Operation-type filtered queries (financial reconciliation)
CREATE INDEX idx_ij_operation_type
    ON inventory_journal(operation_type, created_at DESC);

-- Transfer reconstruction: find both sides of a transfer
CREATE INDEX idx_ij_reference
    ON inventory_journal(reference_id, operation_type)
    WHERE reference_id IS NOT NULL;

-- Variant-level history across all warehouses
CREATE INDEX idx_ij_variant_id
    ON inventory_journal(variant_id, created_at DESC);

COMMENT ON TABLE inventory_journal IS
    'Append-only audit log of all inventory mutations. Never updated or deleted. Application DB user has INSERT + SELECT only. UPDATE and DELETE are revoked.';

COMMENT ON COLUMN inventory_journal.quantity_delta IS
    'Intentionally redundant: quantity_after - quantity_before. Mismatch indicates tampering or data corruption.';

-- ============================================================
-- BULK IMPORT JOBS
-- Tracks async inventory import operations
-- ============================================================
CREATE TABLE bulk_import_jobs (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    version             BIGINT      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Caller-provided idempotency key. Same session = same job.
    -- Prevents duplicate processing on ERP retry.
    import_session_id   UUID        NOT NULL,

    type                VARCHAR(50) NOT NULL DEFAULT 'INVENTORY_ADJUSTMENT',
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    total_rows          INTEGER,
    processed_rows      INTEGER     NOT NULL DEFAULT 0,
    failed_rows         INTEGER     NOT NULL DEFAULT 0,

    -- JSON array: [{row: 5, error: "Variant not found: SKU-XYZ"}, ...]
    error_summary       TEXT,

    submitted_by        VARCHAR(200),
    completed_at        TIMESTAMPTZ,

    CONSTRAINT bulk_import_jobs_pkey
        PRIMARY KEY (id),

    CONSTRAINT bij_status_check
        CHECK (status IN (
            'PENDING', 'PROCESSING', 'COMPLETED',
            'PARTIALLY_FAILED', 'FAILED'
        )),

    CONSTRAINT bij_type_check
        CHECK (type IN ('INVENTORY_ADJUSTMENT'))
);

CREATE UNIQUE INDEX uq_bulk_import_session
    ON bulk_import_jobs(import_session_id);

CREATE INDEX idx_bulk_import_status
    ON bulk_import_jobs(status, created_at DESC);

COMMENT ON COLUMN bulk_import_jobs.import_session_id IS
    'Caller-provided idempotency key. ERP systems retry on network failure. Submitting the same import_session_id is safe — returns the existing job.';

-- ============================================================
-- ADD TRANSFER METHODS TO INVENTORY (new domain operations)
-- No schema change needed — uses existing quantity column.
-- Domain methods added to entity in code below.
-- ============================================================

