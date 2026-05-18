-- ============================================================
-- PRODUCT BULK UPDATE JOBS
-- ============================================================
CREATE TABLE product_bulk_jobs (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    version             BIGINT      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    import_session_id   UUID        NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    total_rows          INTEGER,
    processed_rows      INTEGER     NOT NULL DEFAULT 0,
    failed_rows         INTEGER     NOT NULL DEFAULT 0,

    error_summary       TEXT,

    submitted_by        VARCHAR(200),
    completed_at        TIMESTAMPTZ,

    CONSTRAINT product_bulk_jobs_pkey
        PRIMARY KEY (id),

    CONSTRAINT pbj_status_check
        CHECK (status IN (
            'PENDING', 'PROCESSING', 'COMPLETED',
            'PARTIALLY_FAILED', 'FAILED'
        ))
);

CREATE UNIQUE INDEX uq_product_bulk_session
    ON product_bulk_jobs(import_session_id);

CREATE INDEX idx_product_bulk_status
    ON product_bulk_jobs(status, created_at DESC);

COMMENT ON TABLE product_bulk_jobs IS
    'Tracks asynchronous bulk product updates via CSV.';
