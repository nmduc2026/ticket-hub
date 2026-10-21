-- Stripe Connect Express của organizer (overview.md §6.3, §7.6, ADR 0022): cập nhật từ webhook account.updated.
CREATE TABLE payment.connected_accounts
(
    organizer_id      UUID        NOT NULL,
    stripe_account_id TEXT        NOT NULL UNIQUE,
    charges_enabled   BOOLEAN     NOT NULL DEFAULT FALSE,
    payouts_enabled   BOOLEAN     NOT NULL DEFAULT FALSE,
    details_submitted BOOLEAN     NOT NULL DEFAULT FALSE,
    requirements_due  JSONB       NOT NULL DEFAULT '[]',
    disabled_reason   TEXT,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (organizer_id)
);
