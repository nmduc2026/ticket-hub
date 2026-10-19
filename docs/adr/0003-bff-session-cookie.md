# 0003. BFF + session cookie cho SPA

**Trạng thái:** Accepted

## Bối cảnh
Frontend là SPA React. Token OIDC nằm trong trình duyệt thì có thể bị đánh cắp qua XSS. Ứng dụng có thanh toán và dữ liệu cá nhân nên cần mức bảo vệ cao.

## Các phương án
- **BFF (Backend for Frontend):** Gateway đăng nhập thay SPA, giữ token trong session phía server; trình duyệt chỉ có cookie `SESSION` (HttpOnly) và `XSRF-TOKEN`.
- **Token trong bộ nhớ SPA (PKCE public client):** đơn giản hơn, nhưng token vẫn đọc được bởi script chạy trên trang.

## Quyết định
BFF bằng Spring Cloud Gateway (Server MVC) + OAuth2 Client + Spring Session Redis. Gateway relay token xuống `app` (TokenRelay); `app` là Resource Server kiểm chữ ký, `iss`, `aud=tickethub-api`, hạn. FE và API cùng domain (`/`, `/api`), không cần CORS. Các yêu cầu cấu hình bắt buộc ở `overview.md` §13.1.

## Hệ quả
- (+) SPA không bao giờ cầm access token.
- (+) Refresh token, logout tập trung ở Gateway.
- (−) Phải chống CSRF (cookie `XSRF-TOKEN` + header); webhook Stripe phải được miễn CSRF và session.
- (−) Session phụ thuộc Redis: Redis chết thì người đã đăng nhập mất phiên (§15.2).
- (−) Gateway là một ứng dụng riêng ngay từ đầu (ADR 0015).

Liên quan: `overview.md` §13.1, ADR 0002, 0015.
