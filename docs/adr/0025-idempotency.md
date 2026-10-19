# 0025. Idempotency key dùng chung

**Trạng thái:** Accepted

## Bối cảnh
`POST /bookings`, `POST /payments`, `POST /bookings/{id}/refund` bắt buộc `Idempotency-Key`: mạng chập chờn, khách bấm hai lần hay client retry thì không được tạo hai booking hoặc trừ tiền hai lần.
- Thiết kế cũ: cột `bookings.idempotency_key UNIQUE` toàn cục → hai user trùng key ảnh hưởng nhau; `payments` và `refunds` không có chỗ lưu.
- Thứ tự kiểm tra quan trọng: nếu tra key **sau** khi chạy Lua giữ ghế, lần retry thấy ghế "đã bị giữ" (bởi chính booking của mình) và trả 409 thay vì response cũ.

## Các phương án
- **Cột riêng ở từng bảng:** lặp code ở mỗi endpoint, không phát lại được nguyên response.
- **Một bảng dùng chung**, cài đặt một lần.

## Quyết định
Bảng `idempotency_keys (scope, user_id, key, request_hash, status, response_status, response_body, created_at, expires_at)`, khoá chính `(scope, user_id, key)`, cài đặt một lần trong package `shared`.
- Tra key **trước mọi xử lý nghiệp vụ**.
- Đã `COMPLETED` với cùng `request_hash` → trả lại nguyên response lần đầu. Đang `IN_PROGRESS` → `409 REQUEST_IN_PROGRESS`. Cùng key, body khác → `422 IDEMPOTENCY_KEY_REUSED`.
- Giữ 24 giờ rồi job dọn.
- Lời gọi Stripe dùng idempotency key riêng của Stripe (`booking-{id}`, `refund-{refundId}`).

## Hệ quả
- (+) Mọi endpoint bắt buộc key xử lý giống nhau; retry luôn nhận đúng kết quả cũ.
- (−) Thêm một lần đọc / ghi DB cho mỗi request có key; response lưu trong DB 24 giờ.

Liên quan: `overview.md` §6.7, §7.2, §11.1, §15.1, ADR 0020, 0026.
