-- Doanh thu cho dashboard organizer (overview.md §6.2): cộng khi booking CONFIRMED / REFUNDED và khi vé check-in,
-- trong cùng transaction với chuyển trạng thái nên không cộng hai lần.
CREATE TABLE booking.sales_by_event
(
    event_id           UUID        NOT NULL,
    tier_id            UUID        NOT NULL,
    sold_count         INTEGER     NOT NULL DEFAULT 0 CHECK (sold_count >= 0),
    revenue_minor      BIGINT      NOT NULL DEFAULT 0 CHECK (revenue_minor >= 0),
    fee_minor          BIGINT      NOT NULL DEFAULT 0 CHECK (fee_minor >= 0),
    refunded_count     INTEGER     NOT NULL DEFAULT 0 CHECK (refunded_count >= 0),
    refunded_minor     BIGINT      NOT NULL DEFAULT 0 CHECK (refunded_minor >= 0),
    refunded_fee_minor BIGINT      NOT NULL DEFAULT 0 CHECK (refunded_fee_minor >= 0),
    checked_in_count   INTEGER     NOT NULL DEFAULT 0 CHECK (checked_in_count >= 0),
    updated_at         TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, tier_id)
);

-- Mốc doanh thu theo ngày (nhóm theo ngày Asia/Ho_Chi_Minh). Đơn đã xác nhận trước khi có cột: lấy giờ tạo đơn.
ALTER TABLE booking.bookings ADD COLUMN confirmed_at TIMESTAMPTZ;
UPDATE booking.bookings SET confirmed_at = created_at
WHERE status IN ('CONFIRMED', 'REFUND_REQUESTED', 'REFUNDED', 'REFUND_FAILED');
CREATE INDEX ix_bookings_event_confirmed ON booking.bookings (event_id, confirmed_at) WHERE confirmed_at IS NOT NULL;
