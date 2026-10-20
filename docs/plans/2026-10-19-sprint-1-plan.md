# Plan: Sprint 1. Nền móng

**Mục tiêu:** khung dự án chạy được, đăng nhập qua BFF, CI tự kiểm mọi thay đổi, có image sẵn cho AWS lab 1 (Sprint 2); biết dịch vụ AWS nào dùng được với Free plan.

**Đầu vào:** mỗi hạng mục chỉ đọc đúng các § `overview.md` và ADR ở cột "Đọc"; quy ước Git trong `CLAUDE.md`.

**Ngoài sprint này:** mọi tính năng bán vé, Redis Lua, Kafka, Kubernetes (bắt đầu ở Sprint 3).

## Hạng mục

| # | Việc | Vùng | Đọc | Phụ thuộc | Xong khi |
|---|---|---|---|---|---|
| 1 | Khung monorepo (`backend/`, `frontend/`, `infra/`, `docs/`), `.gitignore`, `.env.example`, gitleaks pre-commit. Docker Compose profile `core`: Postgres (mỗi module một schema), Redis, Keycloak (import realm), Mailpit | Infra | §19.2, §22; ADR 0015, 0017 | — | `docker compose --profile core up` chạy, mọi container healthy |
| 2 | Realm `tickethub` export ở `infra/keycloak/`: client `tickethub-bff` (confidential + PKCE bắt buộc), `tickethub-internal`, `tickethub-account-admin` (chỉ `view-users`, `manage-users`), audience mapper `tickethub-api`, role `CUSTOMER` (mặc định) / `ORGANIZER` / `ADMIN`, 3 user test có `id` cố định (seed của Sprint 2 tham chiếu tới) | Infra | §13.1 (bảng yêu cầu cấu hình), §13.2; ADR 0002, 0003 | 1 | Đăng nhập được 3 user test vào Keycloak; token có `aud=tickethub-api` và đúng role |
| 3 | Backend parent `pom.xml` + Maven Wrapper; `app` với 7 package module, Spring Modulith `verify()` + ArchUnit; Flyway baseline; ProblemDetail (RFC 9457); log JSON có `traceId`; Actuator health | BE | §4.2–4.3, §11.1, §16.1, §22; ADR 0001, 0007, 0012, 0015 | 1 | `./mvnw verify` xanh; test kiến trúc bắt được truy cập sai module |
| 4 | `gateway` BFF theo §13.1: login / logout, PKCE tường minh, token lưu trong session Redis, TokenRelay, 401 cho request XHR, CSRF cookie, miễn CSRF cho `/api/v1/webhooks/**`, header bảo mật | BE | §13.1, §9 (session); ADR 0003 | 2, 3 | Test tích hợp: chưa đăng nhập → 401; đăng nhập xong gọi `/api/**` có Bearer; logout xoá session |
| 5 | `app` là Resource Server (kiểm chữ ký, `iss`, `aud`, role); module `account`: `user_profiles`, đồng bộ khi đăng nhập (§6.6), `GET /api/v1/me` | BE | §6.6, §13.2–13.3; ADR 0016 | 3, 4 | Lần gọi đầu tạo `user_profiles`; đổi tên trong Keycloak → cập nhật sau lần đăng nhập kế tiếp |
| 6 | Bảng `idempotency_keys` + filter dùng chung trong `shared` (§6.7) | BE | §6.7, §11.1; ADR 0025 | 3 | Test: gửi lại cùng key → cùng response; khác body → 422; đang xử lý → 409 |
| 7 | Frontend: Vite + React 19 + TS strict, Tailwind v4 + shadcn, TanStack Router / Query, Zustand, Orval sinh client từ OpenAPI của `app`; i18n `vi` (react-i18next); layout; luồng đăng nhập (401 → chuyển tới Gateway), `GET /me`, bảo vệ route theo role; Vite proxy theo §13.1 | FE | §13.1 (luồng đăng nhập), §14.1–14.3; ADR 0003 | 4, 5 | Đăng nhập / đăng xuất được; vào `/admin` bằng user CUSTOMER bị chặn |
| 7b | Khung giao diện dùng chung theo `docs/design/README.md` §2–3: token sáng / tối; layout khách (header, footer); khung Admin / Organizer (sidebar nhóm, topbar sticky, breadcrumb `Trang chủ ›`, Tìm nhanh Ctrl K, đổi sáng / tối, menu tài khoản, chuông chưa có dữ liệu); toast `sonner`; modal / sheet / popover đóng khi click ra ngoài, nút ✕ và Esc; theme Keycloak (màn #18); hồ sơ cá nhân (màn #19, trừ cài đặt thông báo) | FE | README §2–3; prototype #18, #19 và khung của các màn Admin / Organizer; skill react mục 10 | 7 | Playwright: Ctrl K mở Tìm nhanh, Esc đóng; click ra ngoài đóng menu; đổi theme giữ khi tải lại; ảnh chụp khớp prototype ở 1280px và 390px |
| 8 | Dockerfile `app`, `gateway`, frontend (multi-stage, non-root). CI GitHub Actions: backend build + unit + Testcontainers, Spotless, Error Prone, JaCoCo, SonarCloud; frontend lint, type-check, test; build image, đẩy lên GHCR khi merge vào `main`; gitleaks; Playwright đăng nhập 3 role; Renovate | Infra | §19.3, §19.4 (Image), §20.1; ADR 0007 | 3, 7 | PR mở ra là CI chạy; CI xanh trên `main`; image của cả 3 ứng dụng có trên GHCR |
| 9 | AWS: checklist bảo vệ tài khoản (§23.4 lớp 1–2: MFA, IAM Identity Center, budget 0.01$); Terraform thăm dò ở `infra/terraform/aws/probe/` cho EC2, Spot, RDS, Multi-AZ, ALB, ASG, CloudFront, SES, CloudWatch / SNS, kèm hướng dẫn chạy | Infra | §23; ADR 0013, 0017 | 1 | Người dùng chạy apply / destroy từng dịch vụ; bảng kết quả "dùng được / bị chặn / phương án thay thế"; `verify-clean.sh` sạch |
| 10 | Verify toàn sprint theo AC bên dưới, kèm bằng chứng (skill `verification`) | Verify | AC bên dưới | 1–8 | Báo cáo verify |
| 11 | Review cuối sprint (skill `tech-lead`; review độc lập bằng subagent cho vùng `[USER-REVIEW]`) | Review | — | 10 | APPROVED / CHANGES REQUESTED |

## Thứ tự

1. Việc 1 → 2.
2. Việc 3 chạy song song với 2; việc 9 chạy bất cứ lúc nào sau 1.
3. Việc 4 → 5; việc 6 song song với 4.
4. Việc 7 khi đã có 4 và 5, rồi 7b. Việc 8 bắt đầu từ khi có 3, hoàn thiện sau 7.
5. Việc 10 → 11.

## Acceptance criteria

- AC-1: Người chưa đăng nhập vào trang cần đăng nhập → chuyển tới Keycloak → đăng nhập xong quay lại đúng trang.
- AC-2: Trình duyệt chỉ có cookie `SESSION` và `XSRF-TOKEN`, không thấy access token.
- AC-3: `GET /api/v1/me` trả đúng hồ sơ và role của 3 user test; bản ghi `user_profiles` được tạo.
- AC-4: User `CUSTOMER` không vào được route và API của `ADMIN` (401/403 đúng loại).
- AC-5: Token sai `aud` bị `app` từ chối.
- AC-6: Đăng xuất xoá session ở Gateway và ở Keycloak.
- AC-7: CI chạy trên mọi PR; commit chứa secret bị gitleaks chặn.
- AC-8: Có bảng kết quả kiểm Free plan và mọi tài nguyên thăm dò đã destroy.
- AC-9: Khung Admin / Organizer và layout khách khớp prototype (bố cục, chữ, breadcrumb `Trang chủ ›`); mọi popover / modal đóng được bằng click ra ngoài và Esc, focus trả về nút mở; không có chuỗi viết cứng ngoài i18n.

## Người dùng tự xem (`[USER-REVIEW]`)

- Cấu hình BFF (việc 4) và Resource Server (việc 5).
- Realm export và quyền của client `tickethub-account-admin` (việc 2).
- `.env.example`, gitleaks: không có secret thật trong Git.
- Mọi `terraform plan` của việc 9 trước khi apply.

## Thao tác cần báo trước

Theo `CLAUDE.md`: commit / push / PR / merge, `terraform apply` / `destroy` (việc 9, luôn hỏi từng lần), tạo secret thật (Stripe chưa cần ở sprint này).
