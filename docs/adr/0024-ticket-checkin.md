# 0024. Vé QR ký số, check-in chỉ online

**Trạng thái:** Accepted

## Bối cảnh
Vé phải chống giả và chỉ check-in được 1 lần (BR-09). Vé bị thu hồi khi khách yêu cầu hoàn.
- Vé `REVOKED` vẫn mang chữ ký hợp lệ; JWT trên QR không có `exp` hay trạng thái.
- Nếu cho check-in offline, cùng một ảnh chụp QR vào được nhiều cổng.

## Các phương án
- **Check-in offline:** tải danh sách vé thu hồi trước giờ mở cổng, lưu local các vé đã quét, đồng bộ sau. Chịu được mất mạng, nhưng phức tạp và vẫn hở giữa các thiết bị quét.
- **Check-in chỉ online:** chữ ký để loại vé giả tại chỗ, server là bên quyết định vé dùng được hay không.
- Vai trò nhân viên soát vé riêng, hay để organizer tự quét.

## Quyết định
- QR chứa JWT ký **ES256**: `{ticketId, eventId, seat, kid}`; xoay vòng khoá theo `kid`; public key lấy qua `GET /checkin/keys`. Private key nằm trong Vault (trước khi có Vault: K8s Secret / biến môi trường).
- App check-in (PWA) xác minh chữ ký, rồi gọi `POST /checkins`; server cập nhật atomic `VALID → USED`. **Chỉ cho vào khi server xác nhận**; mất mạng thì báo lỗi.
- **Organizer tự quét**, chưa có vai trò nhân viên soát vé.

## Hệ quả
- (+) Không có cửa cho vé đã thu hồi hay vé dùng lần hai.
- (−) Mất mạng ở cổng thì không check-in được (check-in offline nằm ngoài phạm vi).

Liên quan: BR-09; `overview.md` §1.2, §6.4, §7.5, §13.4, §14.4, ADR 0026.
