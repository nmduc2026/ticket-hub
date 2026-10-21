-- Schema payment (overview.md §6.3): thanh toán, hoàn tiền, khử trùng webhook và khử trùng event.
-- Payment không đọc bảng của Booking: số tiền và chủ booking đến qua BookingApi (ADR 0018).

-- Mỗi booking có tối đa một payment (một PaymentIntent); khách thử lại thẻ khác trên cùng PaymentIntent.
-- Giai đoạn chưa có Connect, `application_fee_minor` chỉ lưu để tính hoàn tiền, không gửi lên Stripe (ADR 0022).
CREATE TABLE payment.payments
(
    id                       UUID        NOT NULL,
    booking_id               UUID        NOT NULL,
    stripe_payment_intent_id TEXT        NOT NULL,
    amount_minor             BIGINT      NOT NULL CHECK (amount_minor > 0), -- BR-10
    application_fee_minor    BIGINT      NOT NULL CHECK (application_fee_minor >= 0), -- BR-06
    currency                 CHAR(3)     NOT NULL DEFAULT 'VND',
    status                   TEXT        NOT NULL
        CHECK (status IN ('REQUIRES_PAYMENT', 'PROCESSING', 'SUCCEEDED', 'CANCELED')),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                  BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_payments_booking UNIQUE (booking_id),
    CONSTRAINT uq_payments_intent UNIQUE (stripe_payment_intent_id),
    CONSTRAINT ck_payments_fee_within_amount CHECK (application_fee_minor <= amount_minor)
);
-- Job đối soát quét payment chưa ở trạng thái kết thúc theo tuổi (§7.3).
CREATE INDEX ix_payments_unsettled ON payment.payments (created_at) WHERE status IN ('REQUIRES_PAYMENT', 'PROCESSING');

-- Mỗi lần thử hoàn là một bản ghi; `id` sinh trước khi gọi Stripe để làm idempotency key `refund-{id}` (ADR 0026).
CREATE TABLE payment.refunds
(
    id               UUID        NOT NULL,
    payment_id       UUID        NOT NULL REFERENCES payment.payments (id),
    stripe_refund_id TEXT,
    amount_minor     BIGINT      NOT NULL CHECK (amount_minor > 0),
    reason           TEXT        NOT NULL,
    status           TEXT        NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_refunds_stripe_refund UNIQUE (stripe_refund_id)
);
CREATE INDEX ix_refunds_payment ON payment.refunds (payment_id);

-- Chống xử lý trùng webhook: ghi `stripe_event_id` trước khi xử lý; gửi lần hai bị bỏ qua.
CREATE TABLE payment.stripe_events
(
    stripe_event_id TEXT        NOT NULL,
    type            TEXT        NOT NULL,
    payload         JSONB       NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ,
    PRIMARY KEY (stripe_event_id)
);

-- Consumer idempotent: ghi (consumer, message_id) trong cùng transaction với xử lý.
CREATE TABLE payment.processed_events
(
    consumer     TEXT        NOT NULL,
    message_id   UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);
