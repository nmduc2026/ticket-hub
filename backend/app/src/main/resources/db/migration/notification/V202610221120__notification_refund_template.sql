-- Email xác nhận đã hoàn tiền (overview.md §7.4).
INSERT INTO notification.templates (code, channel, subject, body_template)
VALUES ('REFUND_COMPLETED', 'EMAIL', 'Đã hoàn tiền cho [(${eventTitle})]', $body$<!DOCTYPE html>
<html lang="vi">
<body style="font-family: Arial, sans-serif; color: #1f2937;">
  <h2>Chúng tôi đã hoàn tiền cho bạn</h2>
  <p>Đơn vé <strong th:text="${eventTitle}">Tên sự kiện</strong> đã được hoàn
    <strong th:text="${amount}">950.000 ₫</strong> về phương thức thanh toán ban đầu.</p>
  <p>Ngân hàng có thể mất vài ngày làm việc để tiền hiện trong tài khoản của bạn.</p>
  <p style="color: #6b7280; font-size: 12px;">Ticket Hub</p>
</body>
</html>$body$);
