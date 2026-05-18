ALTER TABLE product_bulk_jobs
    ADD COLUMN created_by VARCHAR(100),
    ADD COLUMN updated_by VARCHAR(100),
    ADD COLUMN deleted_at TIMESTAMPTZ;
