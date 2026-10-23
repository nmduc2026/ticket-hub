-- Thanh toán đến muộn mà không giữ lại được ghế (overview.md §7.3): booking giữ nguyên EXPIRED / CANCELLED, cột này
-- theo dõi tiền hoàn tự động để khách thấy trên trang đơn. NULL là không có thanh toán đến muộn.
ALTER TABLE booking.bookings
    ADD COLUMN late_payment TEXT CHECK (late_payment IN ('REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED'));
