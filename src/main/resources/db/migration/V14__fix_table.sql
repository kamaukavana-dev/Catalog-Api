-- Add standard audit columns for BaseEntity-mapped tables.
-- product_bulk_jobs extends BaseEntity and requires these columns for schema validation.
--
-- IF NOT EXISTS: some dev databases had these columns added manually ("rogue SQL")
-- before this change was formalised into a migration, so V14 must be idempotent.
-- V14 has never been recorded in any flyway_schema_history, so changing its body
-- here introduces no checksum drift. This mirrors the sibling table's V16 migration.
ALTER TABLE product_bulk_jobs
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);

ALTER TABLE product_bulk_jobs
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100);

ALTER TABLE product_bulk_jobs
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
