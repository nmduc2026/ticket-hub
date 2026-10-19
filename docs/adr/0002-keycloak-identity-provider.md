# 0002. Keycloak làm Identity Provider

**Trạng thái:** Accepted

## Bối cảnh
Cần đăng nhập OIDC, 3 role (`CUSTOMER`, `ORGANIZER`, `ADMIN`), trang tự quản lý tài khoản, API quản trị để khoá tài khoản và gán role, chạy được local miễn phí.

## Các phương án
- **Keycloak:** chuẩn ngành, có sẵn đăng ký / quên mật khẩu / trang tài khoản / admin console / Admin API; realm export được để dựng lại.
- **Spring Authorization Server:** nhẹ, cùng hệ Spring, nhưng phải tự làm đăng ký, quên mật khẩu, quản trị người dùng.
- **Dịch vụ ngoài (Auth0, Cognito):** ít việc vận hành, nhưng phụ thuộc bên ngoài và giới hạn gói miễn phí.

## Quyết định
Keycloak, realm `tickethub`; client `tickethub-bff` (confidential, Authorization Code + PKCE), `tickethub-internal` (client credentials), `tickethub-account-admin` (service account chỉ có `view-users`, `manage-users`). Realm export lưu ở `infra/keycloak/`.

## Hệ quả
- (+) Có ngay đăng ký, trang tài khoản, Admin API cho module `account`.
- (+) Học được OIDC chuẩn, dùng lại được ở dự án khác.
- (−) Thêm một thành phần phải chạy (~0.5–1 GB RAM) và cấu hình đúng (audience mapper, PKCE, role mapping, §13.1).
- (−) Keycloak chết thì không đăng nhập mới được (§15.2).

Liên quan: `overview.md` §13.1, §13.2, ADR 0003, 0016.
