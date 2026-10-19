# 0016. Đồng bộ người dùng từ Keycloak (module `account`)

**Trạng thái:** Accepted

## Bối cảnh
Tài khoản nằm trong Keycloak; DB của app chỉ có `user_id`. App cần email để gửi vé, và Admin cần tìm khách, xem tên trong audit log, xem danh sách người dùng, khoá tài khoản.

## Các phương án
- **A. Chỉ lưu email theo đơn** (`bookings.contact_email`): đủ để gửi vé, nhưng Admin không tra cứu được người dùng.
- **B. Hỏi Keycloak Admin API mỗi lần cần:** luôn mới nhất, nhưng thêm phụ thuộc vào Keycloak và chậm.
- **C1. Bản sao `user_profiles` + email theo đơn:** app có bảng hồ sơ để tra cứu; email gửi vé vẫn lấy theo đơn.
- **C2. Chỉ bản sao `user_profiles`:** gửi vé theo email hiện tại trong hồ sơ.
- Cách đồng bộ: khi đăng nhập (claim của token), plugin Keycloak đẩy sang, hoặc job định kỳ kéo về.

## Quyết định
**C1, đồng bộ khi đăng nhập.** Module `account` sở hữu `user_profiles` (email, tên, username, roles, enabled). Request đầu tiên tạo bản ghi; các request sau so claim, chỉ ghi khi khác. Keycloak vẫn là nguồn sự thật. Đơn hàng lưu `contact_email` lúc mua. Duyệt organizer cập nhật role ngay qua event `OrganizerApproved`. Khoá / mở khoá tài khoản qua client `tickethub-account-admin`.

## Hệ quả
- (+) Màn hình người dùng của Admin, audit log, tra cứu chạy trên DB của app, không phụ thuộc Keycloak.
- (+) Không cần plugin Keycloak hay job đồng bộ.
- (−) Hồ sơ có thể cũ cho tới lần đăng nhập kế tiếp; người chưa từng đăng nhập vào app chưa có hồ sơ.
- (−) Email là dữ liệu cá nhân nằm ở 2 nơi; event mang `contact_email` cần thời hạn lưu.

Liên quan: `overview.md` §6.2, §6.6, §7.6, §13.2, ADR 0002.
