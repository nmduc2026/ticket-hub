# Báo cáo verify Sprint 1

Nhánh `feature/sprint-1`. Chạy ngày 20/10/2026 trên máy dev (Windows 11, Docker Desktop, compose profile `core`). Plan: `docs/plans/2026-10-19-sprint-1-plan.md`, hạng mục 10.

## Lệnh đã chạy

| Lệnh | Kết quả |
|---|---|
| `cd backend && ./mvnw verify` (Error Prone, test, Spotless, JaCoCo) | Xanh. 59 test: `app` 52, `gateway` 7 |
| `cd frontend && npx tsc -b` | Không lỗi |
| `cd frontend && npx oxlint` | Không cảnh báo |
| `cd frontend && npx vitest run` | 14 test xanh |
| `npm run api:generate` sau `mvnw verify` | Không có thay đổi (hợp đồng OpenAPI và client Orval khớp) |
| `cd frontend && npx playwright test` (stack thật: Keycloak, Postgres, Redis, `app`, `gateway`, Vite) | 23 xanh, 3 bỏ qua có chủ đích (test chỉ áp dụng cho một khổ màn hình) ở 2 khổ 1280px và 390px |
| `docker build` 3 image + Trivy (`--severity CRITICAL --ignore-unfixed`) | 3 image build được, không còn CRITICAL |
| `actionlint` trên `.github/workflows/ci.yml` | Sạch |
| `terraform validate` + `fmt -check` cho 9 thư mục thăm dò | Hợp lệ |
| Thử commit khoá Stripe live giả ngẫu nhiên | gitleaks chặn (`stripe-access-token`, exit 1) |

## Truy vết AC

| AC | Bằng chứng | Kết quả |
|---|---|---|
| AC-1 Vào trang cần đăng nhập → Keycloak → quay lại đúng trang | `e2e/auth.spec.ts` "chưa đăng nhập vào /admin được đưa tới Keycloak rồi quay lại đúng trang"; `BffLoginFlowTests` (returnTo, chặn `//host`); chạy tay với Keycloak thật | Đạt |
| AC-2 Trình duyệt chỉ có cookie `SESSION` và `XSRF-TOKEN`, không thấy token | `e2e/auth.spec.ts` kiểm tên cookie của origin ứng dụng, `SESSION` là HttpOnly, storage không chứa "token"; `BffLoginFlowTests` | Đạt (cookie của Keycloak ở origin `:8180` nên không lẫn; ở dev cùng host `localhost` DevTools vẫn thấy, đã ghi ở §13.1) |
| AC-3 `GET /api/v1/me` trả đúng hồ sơ và role 3 user; tạo `user_profiles` | `MeEndpointTests` (7 test); chạy tay: đăng nhập `customer`, `organizer`, `admin` qua Gateway, 3 dòng trong DB, đổi tên bằng Keycloak Admin API rồi đăng nhập lại thì `/me` và DB đổi theo | Đạt |
| AC-4 `CUSTOMER` không vào được route và API của `ADMIN` | `ResourceServerSecurityTests` (401 khi không token, 403 sai role, role lạ như `offline_access` không thành quyền); `routing.test.tsx`, `guards.test.ts`; `e2e` "CUSTOMER bị chặn ở /admin và /organizer" | Đạt |
| AC-5 Token sai `aud` bị `app` từ chối | `ResourceServerSecurityTests`: sai `aud`, không có `aud`, sai `iss`, hết hạn, giả chữ ký đều 401 | Đạt |
| AC-6 Đăng xuất xoá session ở Gateway và Keycloak | `BffLoginFlowTests` (logout trả `logoutUrl` cho XHR, gọi lại `/api/**` bị 401); `e2e` "đăng xuất xoá session ở Gateway và phiên SSO ở Keycloak" (vào lại thì Keycloak đòi mật khẩu) | Đạt |
| AC-7 CI chạy trên mọi PR; commit chứa secret bị gitleaks chặn | gitleaks chặn commit chứa khoá thật dạng Stripe (xem trên). Workflow đã viết và qua actionlint | **Một phần**: chưa chạy được CI vì chưa push (xem việc treo) |
| AC-8 Có bảng kết quả Free plan, mọi tài nguyên thăm dò đã destroy | Terraform thăm dò, `run-probe.sh`, `verify-clean.sh`, bảng `docs/ops/aws-free-plan-probe.md` đã có | **Chưa đạt**: chưa chạy `apply` (luôn cần người dùng xác nhận từng lần) |
| AC-9 Khung Admin / Organizer, layout khách khớp prototype; popover / modal đóng bằng click ra ngoài và Esc, trả focus; không chuỗi cứng ngoài i18n | `e2e/shell.spec.ts` (Ctrl K mở và Esc đóng; nút Tìm nhanh trả focus; chuông đóng bằng click ngoài và Esc, focus về nút; theme giữ sau tải lại; ngăn kéo ở 390px; breadcrumb `Trang chủ ›`); ảnh chụp 1280px và 390px đối chiếu prototype bằng mắt; quét chuỗi cứng chỉ còn thông báo lỗi dành cho dev, comment và dữ liệu test | Đạt (đối chiếu prototype là xem ảnh, chưa so từng pixel) |

## Review nhanh vùng rủi ro (bảo mật, secret, AWS)

Kết luận: **APPROVED, kèm việc treo** (`docs/plans/2026-10-21-pending-sprint-1-4.md`, mục Sprint 1). Chưa gọi subagent review độc lập vì Sprint 1 chưa có tiền hay ghế; sẽ làm từ Sprint 2 cho vùng tiền / oversell.

- Secret: không có secret thật trong repo; `.env` bị ignore; `.env.example`, realm và test chỉ có giá trị giả cho máy dev. gitleaks chạy ở pre-commit và CI.
- Gateway: PKCE S256, token chỉ ở session Redis, cookie `SESSION` HttpOnly SameSite=Lax, CSRF double-submit, `returnTo` chỉ nhận đường dẫn nội bộ, bỏ `Cookie` trước khi chuyển xuống `app`, webhook miễn CSRF và không session.
- `app`: kiểm chữ ký, `iss`, `aud`, hạn; role chỉ nhận 3 giá trị; 401 / 403 theo Problem Details; mặc định mọi đường dẫn đòi đăng nhập.
- Idempotency: key theo `(scope, user_id)`; 5xx không lưu; bản `IN_PROGRESS` bị bỏ rơi được claim lại sau 2 phút. Giới hạn đã biết: việc ghi nghiệp vụ và việc đánh dấu `COMPLETED` không cùng một transaction, nên nghiệp vụ vẫn phải có ràng buộc duy nhất ở DB (đúng thiết kế §10).
- Gateway chưa có rate limit (Bucket4j, ghi ở roadmap), nằm ngoài Sprint 1.
- Image: distroless / nginx non-root; đã nâng Tomcat và Jackson để hết CVE CRITICAL. Image nền distroless còn vài HIGH chưa có bản vá từ upstream; CI chỉ chặn CRITICAL.
- AWS: chỉ chuẩn bị. Terraform dùng VPC mặc định, không NAT, không IP công khai, `skip_final_snapshot`; script luôn hỏi trước apply.
