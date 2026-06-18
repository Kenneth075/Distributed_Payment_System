--Initial schema for payment authorization service.

-- -------------------------------------------------------
-- CARDS table
-- Represents registered cards with their spending limits.
-----------------------------------------------------------

CREATE TABLE cards(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_number VARCHAR(19) NOT NULL UNIQUE, --mask e.g "1234 **** **** 3445"
    holder_name VARCHAR(50) NOT NULL,
    daily_limit NUMERIC(12, 2) NOT NULL DEFAULT 100000.00,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- seed cards table for demo purpose.
INSERT INTO cards (id, card_number, holder_name, daily_limit, is_active) VALUES
                                                                             ('a1b2c3d4-0000-0000-0000-000000000001', '1234-****-****-4534', 'Kenneth Edoho', 200000, TRUE),
                                                                             ('a1b2c3d7-0000-0000-0000-000000000002', '1634-****-****-8953', 'Kenny Codez', 100000, TRUE),
                                                                             ('a1b2c3d9-0000-0000-0000-000000000003', '3714-****-****-3333', 'Jones Mile', 5000.00, FALSE);

-- -------------------------------------------------------
-- PAYMENTS table
-- Central record of every authorization attempt.
-- -------------------------------------------------------

CREATE TABLE payments(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE, --client supply, prevents duplicate charges.
    card_id UUID NOT NULL REFERENCES cards(id),
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'NGN',
    merchant_id VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    -- Saga state machine:
    --   PENDING      -> just received, validating
    --   AUTHORIZED   -> passed validation, funds reserved on ledger
    --   SETTLING     -> settlement call in progress
    --   SETTLED      -> fully complete
    --   DECLINED     -> failed validation or card check
    --   COMPENSATED  -> settlement failed, funds reversed on ledger
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING',

    -- Response details
    auth_code        VARCHAR(50),    -- returned to client on success
    decline_reason   VARCHAR(255),   -- returned to client on failure

    -- Audit timestamps
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Index for Idempotency lookups (most frequency query)
CREATE INDEX idx_payments_idempotency_key ON payments(idempotency_key);

-- Index for card-based queries (e.g. daily spend check)
CREATE INDEX idx_payments_card_id ON payments(card_id);

-- -------------------------------------------------------
-- PAYMENT_EVENTS table
-- Append-only audit log of every status transition.
-- Essential for distributed system debugging.
-- -------------------------------------------------------

CREATE TABLE payment_events(
    id BIGSERIAL PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    event_type VARCHAR(50) NOT NULL, -- e.g. VALIDATION_PASSED, LEDGER_RESERVED, COMPENSATION_TRIGGERED
    details TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_events_payment_id ON payment_events(payment_id);
