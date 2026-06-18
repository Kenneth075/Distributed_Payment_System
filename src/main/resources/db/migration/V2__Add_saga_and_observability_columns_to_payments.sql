-- ALTER TABLE payments
--     ADD COLUMN ledger_reservation_id VARCHAR(255),
--     ADD COLUMN ledger_attempts INT NOT NULL DEFAULT 0,
--     ADD COLUMN settled_at TIMESTAMPTZ,
--     ADD COLUMN compensated_at TIMESTAMPTZ,
--     ADD COLUMN compensation_reason VARCHAR(255);

-- Add missing columns to payments table
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS ledger_reservation_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ledger_attempts INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS settled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS compensated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS compensation_reason VARCHAR(255);