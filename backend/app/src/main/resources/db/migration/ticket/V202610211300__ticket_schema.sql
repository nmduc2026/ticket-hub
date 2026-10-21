-- Schema ticket (overview.md §6.4): vé, khoá ký QR và khử trùng event.
-- Ticket không đọc bảng của Booking: thông tin hiển thị của vé (sự kiện, giờ, địa điểm, ghế) đến qua payload
-- BookingConfirmed và được lưu cùng vé.

-- Mỗi ghế đã bán một vé. `qr_token` là JWT ES256 {ticketId, eventId, seat, kid}; `key_id` là khoá đã ký (ADR 0024).
CREATE TABLE ticket.tickets
(
    id            UUID        NOT NULL,
    booking_id    UUID        NOT NULL,
    event_id      UUID        NOT NULL,
    seat_id       UUID        NOT NULL,
    owner_user_id UUID        NOT NULL,
    status        TEXT        NOT NULL CHECK (status IN ('VALID', 'USED', 'REVOKED')),
    qr_token      TEXT        NOT NULL,
    key_id        TEXT        NOT NULL,
    event_title   TEXT        NOT NULL,
    starts_at     TIMESTAMPTZ NOT NULL,
    venue_name    TEXT        NOT NULL,
    section_code  TEXT        NOT NULL,
    row_label     TEXT        NOT NULL,
    seat_number   INTEGER     NOT NULL,
    tier_name     TEXT        NOT NULL,
    issued_at     TIMESTAMPTZ NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
-- BR-01: chốt chặn oversell cuối cùng. Một ghế chỉ có một vé chưa thu hồi.
CREATE UNIQUE INDEX uq_ticket_seat_active ON ticket.tickets (event_id, seat_id) WHERE status <> 'REVOKED';
CREATE INDEX ix_tickets_booking ON ticket.tickets (booking_id);
CREATE INDEX ix_tickets_owner ON ticket.tickets (owner_user_id, issued_at DESC);

-- Khoá công khai theo `key_id` để xác minh QR; khoá cũ giữ lại (RETIRED) để vé đã phát vẫn xác minh được.
-- Khoá riêng không nằm ở DB: biến môi trường / K8s Secret (trước khi có Vault).
CREATE TABLE ticket.signing_keys
(
    key_id     TEXT        NOT NULL,
    public_key TEXT        NOT NULL, -- JWK công khai (JSON)
    status     TEXT        NOT NULL CHECK (status IN ('ACTIVE', 'RETIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (key_id)
);
-- Chỉ một khoá ACTIVE tại một thời điểm.
CREATE UNIQUE INDEX uq_signing_key_active ON ticket.signing_keys (status) WHERE status = 'ACTIVE';

-- Consumer idempotent: ghi (consumer, message_id) trong cùng transaction với xử lý.
CREATE TABLE ticket.processed_events
(
    consumer     TEXT        NOT NULL,
    message_id   UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);
