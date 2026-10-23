-- Email "sắp hết giờ giữ ghế" (overview.md §6.5): loại tuỳ chọn, chỉ gửi khi khách bật email cho loại này.
INSERT INTO notification.templates (code, channel, subject, body_template)
VALUES ('HOLD_EXPIRING', 'EMAIL', 'Sắp hết giờ giữ ghế của bạn', $body$<!DOCTYPE html>
<html lang="vi">
<body style="font-family: Arial, sans-serif; color: #1f2937;">
  <h2>Ghế của bạn sắp được nhả</h2>
  <p>Ghế bạn đang giữ cho <strong th:text="${eventTitle}">Tên sự kiện</strong> sẽ được nhả lúc
     <strong th:text="${expiresAt}">20:00</strong> nếu chưa thanh toán.</p>
  <p><a th:href="${checkoutUrl}">Thanh toán ngay</a></p>
  <p style="color: #6b7280; font-size: 12px;">Ticket Hub</p>
</body>
</html>$body$);
