-- Email kết quả duyệt hồ sơ organizer (overview.md §7.6): duyệt và từ chối kèm lý do.
INSERT INTO notification.templates (code, channel, subject, body_template)
VALUES ('ORGANIZER_APPROVED', 'EMAIL', 'Hồ sơ ban tổ chức của bạn đã được duyệt', $body$<!DOCTYPE html>
<html lang="vi">
<body style="font-family: Arial, sans-serif; color: #1f2937;">
  <h2>Chúc mừng, hồ sơ đã được duyệt</h2>
  <p><strong th:text="${displayName}">Tên ban tổ chức</strong> đã trở thành ban tổ chức trên Ticket Hub.</p>
  <p>Hãy đăng nhập lại để thấy menu Ban tổ chức, rồi kết nối Stripe để nhận thanh toán và publish sự kiện.</p>
  <p><a th:href="${organizerUrl}">Mở trang Ban tổ chức</a></p>
  <p style="color: #6b7280; font-size: 12px;">Ticket Hub</p>
</body>
</html>$body$),
       ('ORGANIZER_REJECTED', 'EMAIL', 'Hồ sơ ban tổ chức của bạn chưa được duyệt', $body$<!DOCTYPE html>
<html lang="vi">
<body style="font-family: Arial, sans-serif; color: #1f2937;">
  <h2>Hồ sơ chưa được duyệt</h2>
  <p>Hồ sơ của <strong th:text="${displayName}">Tên ban tổ chức</strong> chưa được duyệt vì lý do sau:</p>
  <blockquote style="border-left: 3px solid #d1d5db; margin: 0; padding-left: 12px;" th:text="${reason}">Lý do</blockquote>
  <p>Bạn có thể chỉnh sửa và gửi lại hồ sơ.</p>
  <p><a th:href="${applyUrl}">Gửi lại hồ sơ</a></p>
  <p style="color: #6b7280; font-size: 12px;">Ticket Hub</p>
</body>
</html>$body$);
