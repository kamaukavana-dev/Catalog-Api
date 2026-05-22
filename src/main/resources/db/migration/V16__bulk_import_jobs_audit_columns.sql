-- Add standard audit columns for BaseEntity-mapped tables.
-- bulk_import_jobs extends BaseEntity and requires these columns for schema validation.
ALTER TABLE bulk_import_jobs
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);

ALTER TABLE bulk_import_jobs
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100);

ALTER TABLE bulk_import_jobs
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

