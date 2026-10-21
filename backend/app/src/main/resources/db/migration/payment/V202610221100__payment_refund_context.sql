-- Ngữ cảnh của lần hoàn (overview.md §7.4): Payment lấy từ RefundRequested để RefundCompleted / RefundFailed mang đủ
-- người nhận và tên sự kiện cho Booking và Notification, không phải đọc dữ liệu của module khác.
-- Hoàn tự động vì thanh toán đến muộn (BookingConfirmationFailed) không có các trường này nên để trống.
ALTER TABLE payment.refunds
    ADD COLUMN booking_id     UUID,
    ADD COLUMN user_id        UUID,
    ADD COLUMN contact_email  TEXT,
    ADD COLUMN event_title    TEXT,
    ADD COLUMN failure_reason TEXT;
