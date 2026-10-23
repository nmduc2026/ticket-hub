-- Hàng đợi hoàn tiền khi huỷ sự kiện (BR-08, plan Sprint 6): job ShedLock rút theo lô, giới hạn tốc độ gọi Stripe.
-- Mỗi booking một dòng (gửi lại event không xếp hai lần); lease chống hai pod xử lý trùng, pod chết thì lease hết
-- hạn và dòng được thử lại (an toàn nhờ idempotency key refund-{refundId}).
CREATE TABLE payment.refund_queue
(
    id              UUID        NOT NULL,
    booking_id      UUID        NOT NULL UNIQUE,
    user_id         UUID,
    contact_email   TEXT,
    event_title     TEXT,
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor >= 0),
    currency        CHAR(3)     NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
    attempts        INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_refund_queue_due ON payment.refund_queue (next_attempt_at) WHERE status = 'PENDING';
