-- Hoàn vé (overview.md §7.4, ADR 0026): lý do và số tiền hoàn chốt lúc khách yêu cầu, và các vé đã được Ticket thu hồi.
-- Ghế chỉ về AVAILABLE khi mọi vé của booking đã được thu hồi.

ALTER TABLE booking.bookings
    ADD COLUMN refund_reason       TEXT,
    ADD COLUMN refund_amount_minor BIGINT CHECK (refund_amount_minor >= 0), -- BR-10
    ADD COLUMN refund_requested_at TIMESTAMPTZ;

-- Mỗi vé đã thu hồi một dòng; đủ số dòng bằng số ghế của booking thì nhả ghế. Khoá chính chống đếm trùng khi event gửi lại.
CREATE TABLE booking.revoked_tickets
(
    booking_id UUID        NOT NULL REFERENCES booking.bookings (id),
    ticket_id  UUID        NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (booking_id, ticket_id)
);
