-- Allow the newer in-flight status IN_PROGRESS while remaining backward compatible
-- with legacy status PROCESSING.
ALTER TABLE bulk_import_jobs
    DROP CONSTRAINT IF EXISTS bij_status_check;

ALTER TABLE bulk_import_jobs
    ADD CONSTRAINT bij_status_check
        CHECK (status IN (
            'PENDING', 'PROCESSING', 'IN_PROGRESS', 'COMPLETED',
            'PARTIALLY_FAILED', 'FAILED'
        ));

