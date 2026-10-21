-- Email "tạm dừng bán" (BR-13): loại tuỳ chọn, gửi khi khách bật email cho loại này.
INSERT INTO notification.templates (code, channel, subject, body_template)
VALUES ('SALE_PAUSED', 'EMAIL', 'Sự kiện tạm dừng bán', $body$<!DOCTYPE html>
<html lang="vi">
<body style="font-family: Arial, sans-serif; color: #1f2937;">
  <h2>Sự kiện tạm dừng bán</h2>
  <p><strong th:text="${eventTitle}">Tên sự kiện</strong> đang tạm dừng bán vé.</p>
  <p th:if="${confirmed}">Vé bạn đã mua vẫn giữ nguyên. Ticket Hub sẽ báo khi có thay đổi.</p>
  <p th:unless="${confirmed}">Đơn đang giữ ghế của bạn có thể không thanh toán được; ghế sẽ được nhả khi hết hạn giữ.</p>
  <p><a th:href="${bookingsUrl}">Xem đơn của tôi</a></p>
  <p style="color: #6b7280; font-size: 12px;">Ticket Hub</p>
</body>
</html>$body$);
