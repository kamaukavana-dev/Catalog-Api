CREATE INDEX IF NOT EXISTS idx_inventory_reservations_status_expires_at
ON inventory_reservations (status, expires_at)
WHERE status = 'ACTIVE';
