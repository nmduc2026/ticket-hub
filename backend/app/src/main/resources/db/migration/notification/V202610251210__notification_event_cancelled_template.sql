-- Email bắt buộc khi sự kiện bị huỷ (BR-08): vé thu hồi, hoàn 100% gồm phí nền tảng.
INSERT INTO notification.templates (code, channel, subject, body_template)
VALUES ('EVENT_CANCELLED', 'EMAIL', 'Sự kiện đã bị huỷ, bạn được hoàn tiền', $body$<!DOCTYPE html>
<html lang="vi">
<body style="font-family: Arial, sans-serif; color: #1f2937;">
  <h2>Sự kiện đã bị huỷ</h2>
  <p><strong th:text="${eventTitle}">Tên sự kiện</strong> đã bị huỷ. Vé của bạn không còn hiệu lực.</p>
  <p>Bạn được hoàn toàn bộ <strong th:text="${amount}">1.000.000 ₫</strong>, gồm cả phí nền tảng. Tiền về phương thức
     thanh toán ban đầu trong vài ngày làm việc.</p>
  <p><a th:href="${bookingsUrl}">Xem đơn của tôi</a></p>
  <p style="color: #6b7280; font-size: 12px;">Ticket Hub</p>
</body>
</html>$body$);
