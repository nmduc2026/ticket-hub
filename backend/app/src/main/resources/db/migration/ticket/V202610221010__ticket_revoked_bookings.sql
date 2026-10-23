-- Booking đã yêu cầu hoàn trước khi Ticket kịp phát vé (BookingConfirmed đến sau RefundRequested): ghi lại để vé phát
-- sau đó ra đời đã ở trạng thái REVOKED (overview.md §7.4, BR-09).
CREATE TABLE ticket.revoked_bookings
(
    booking_id UUID        NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (booking_id)
);
