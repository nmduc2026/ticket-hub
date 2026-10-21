-- Thời điểm hoàn thất bại gần nhất, để Admin thấy đơn nào cần hoàn thủ công trước (overview.md §7.4).
ALTER TABLE booking.bookings
    ADD COLUMN refund_failed_at TIMESTAMPTZ;
