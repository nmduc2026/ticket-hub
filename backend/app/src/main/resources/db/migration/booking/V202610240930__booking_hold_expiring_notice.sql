-- Mốc đã báo "sắp hết giờ giữ ghế" (overview.md §6.5): mỗi booking chỉ phát BookingHoldExpiring một lần.
ALTER TABLE booking.bookings ADD COLUMN hold_expiring_notified_at TIMESTAMPTZ;
