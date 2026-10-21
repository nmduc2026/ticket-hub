# Việc còn treo: Sprint 3

Ghi những việc chưa xong hoặc cần người dùng làm. Xoá dòng khi xong. Test cần Docker viết ở Sprint 3 được ghi ở bảng "Test viết nhưng chưa chạy".

## Cần người dùng

| # | Việc | Vì sao treo | Cách làm |
|---|---|---|---|
| 1 | Tạo tài khoản Stripe test mode (Singapore), đặt `STRIPE_SECRET_KEY` và `STRIPE_WEBHOOK_SECRET` vào `.env` | Câu hỏi mở BLOCKING trước việc 2; Claude không tạo tài khoản thay | Dashboard Stripe → Developers → API keys; `stripe listen` cho webhook secret |
| 3 | Rebase `feature/sprint-3` lên `main` khi PR Sprint 2 được squash merge | Nhánh Sprint 3 tách từ `feature/sprint-2` | `git rebase --onto main <commit-cuối-sprint-2> feature/sprint-3` |

## Test viết nhưng chưa chạy (cần Docker)

| Hạng mục | Test | Cần |
|---|---|---|
| 1 | Gọi `StripeGateway` trên stripe-mock thật (đã chạy `StripeGatewayTests` trên WireMock, xanh 21/10) | stripe-mock (chưa có trong compose) |

## Việc kỹ thuật còn lại

| # | Việc | Ghi chú |
|---|---|---|
| 1 | `spotless:check` đỏ ở `PublicEventController.java` | Có từ Sprint 2 (CRLF trên Windows, xem pending Sprint 2 mục 11); file của hạng mục 1 sạch |
| 2 | Thêm `STRIPE_*` vào `infra/docker-compose/.env.example` và service stripe-mock vào compose | Chưa làm; cần khi chạy integration test |
| 4 | `booking` vẫn dùng JdbcTemplate (chưa chuyển sang JPA) | SQL giữ ghế (`FOR UPDATE`, `UPDATE ... WHERE status = 'AVAILABLE'`, `RETURNING`, upsert `ON CONFLICT ... WHERE`) là chỗ đặc thù. Chuyển sang entity + Spring Data (giữ nguyên SQL bằng native query) làm được khi bạn muốn: đã có Docker, test 50 luồng và truy vấn ghế trùng (xanh 21/10) làm lưới an toàn |
| 5 | `shared/idempotency`, `shared/storage` giữ dạng gói theo tính năng, chưa chia `config` / `util` | Chia nếu bạn muốn đồng nhất |
| 6 | Thêm `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `TICKET_SIGNING_JWK`, `MAIL_*` vào `.env.example` và truyền vào `app` khi chạy compose | Chưa làm; `TICKET_SIGNING_JWK` để trống thì Ticket sinh khoá tạm (vé phát trước đó không xác minh được sau khi khởi động lại) |
| 7 | Phát vé vi phạm UNIQUE → `TicketIssuanceFailed` → hoàn tự động | Sprint 4; hiện vi phạm chỉ làm giao dịch lỗi (index DB vẫn chặn oversell) |
| 3 | Retry / circuit breaker / bulkhead cho `StripeGateway` | Sprint 4 (Resilience4j); hiện chỉ có timeout 3 giây, SDK không tự retry |

## Hạng mục 9–13 (FE, Helm, ArgoCD)

| # | Việc | Loại | Ghi chú |
|---|---|---|---|
| 9 | Duyệt `docs/design/checkout.md` (các chỗ ghi "Cần duyệt": chỉ hiện email, bỏ họ tên / SĐT; tab vé và lọc đơn chỉ ở client) | Người dùng | Xong khi bạn duyệt |
| 10 | Đặt `VITE_STRIPE_PUBLISHABLE_KEY` (pk_test_…) cho build / dev; chưa đặt thì form thanh toán báo không tải được | Người dùng | Chưa có `frontend/.env.example`; thêm khi đặt khoá |
| 10 | Chưa kiểm trên trình duyệt thật (Playwright MCP): bố cục so với prototype #6/#7, bàn phím, breakpoint 390px, thẻ 4242 thật | Verify | Làm ở hạng mục 14–15 cùng Stripe key |
| 10 | Component test dùng Stripe giả (mock `@stripe/react-stripe-js`); Payment Element thật chưa được chạy | Verify | Playwright hạng mục 14 |
| 10 | Đơn `EXPIRED`/`CANCELLED` nhận thanh toán đến muộn: FE chỉ chờ, chưa hiện "không giữ lại được ghế, sẽ hoàn tiền" vì booking chưa có tín hiệu | Thiết kế | Cần API báo `BookingConfirmationFailed` / hoàn tiền, đề xuất Sprint 4 |
| 10 | Mở lại trang thanh toán sau khi Stripe đã nhận tiền nhưng chưa CONFIRMED sẽ hiện lại form | Thiết kế | PI đã `succeeded`; cần `GET` trạng thái payment |
| 11 | Vé và đơn lấy một trang lớn (100 vé) / phân trang theo server (10 đơn), tab / tìm / lọc làm ở client | Thiết kế | API chưa có lọc; thêm khi cần |
| 11 | "Tải PDF", "Thêm vào lịch", hoàn vé, hoá đơn, chi tiết đơn: ẩn | Phạm vi | Sprint 4 hoặc sau |
| 12 | Chưa có k3d và cụm; chỉ chạy `helm lint` / `helm template` (Helm 4.3.1 cài bằng scoop). Chưa chạy: dựng cụm, app khởi động, probe, đăng nhập | Người dùng | Làm theo `docs/ops/k8s-staging.md` trên máy staging, tạo Secret thật |
| 12 | Đăng nhập trong cụm: Gateway phải phân giải `auth.tickethub.localhost` (CoreDNS rewrite về Envoy Gateway) | Thiết kế | Chưa làm; kiểm khi chạy cụm |
| 12 | Image GHCR `ghcr.io/nmduc2026/ticket-hub-*` chỉ có sau khi main chạy CI; trước đó `ImagePullBackOff` | Phụ thuộc | PR Sprint 2–3 chưa merge |
| 13 | Cài ArgoCD, `kubectl apply root.yaml`, hosts `*.tickethub.localhost`, tin CA tự ký | Người dùng | Báo trước, `docs/ops/gitops.md` |
| 13 | Job `update-image-tags` push vào `main`: bị chặn nếu bật bắt buộc PR | Cấu hình | Cho phép github-actions bỏ qua, hoặc đổi sang PR tự động |
| 13 | Secret `NVD_API_KEY` cho OWASP Dependency-Check (chưa có thì job bỏ qua) | Người dùng | nvd.nist.gov/developers/request-an-api-key |
| 13 | Version chart Envoy Gateway v1.5.1, cert-manager v1.18.2, ArgoCD v3.1.5 chưa xác nhận trên mạng | Verify | Kiểm khi cài |
| 13 | YAML mới chỉ kiểm cú pháp (PyYAML), chưa `kubectl --dry-run=server` | Verify | Cần cụm |

## Hạng mục 14 (E2E mua vé)

| # | Việc | Loại | Ghi chú |
|---|---|---|---|
| 14 | `frontend/e2e/purchase.spec.ts` đã viết, chưa chạy: bỏ qua trừ khi `E2E_STRIPE=1`. Cần Stripe test key, `stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe`, `app` chạy với `STRIPE_*` và profile `seed`, `VITE_STRIPE_PUBLISHABLE_KEY` | Người dùng | Selector iframe Stripe (`[name="number"]`…) chưa kiểm trên Payment Element thật |
| 14 | Đường webhook trong lệnh `stripe listen` lấy theo route Gateway `/api/v1/webhooks/**`; xác nhận lại đúng đường của controller | Verify | |
| 14 | Truy vấn ghế trùng chạy bằng `docker compose exec postgres psql`, cần compose `core` đang chạy | Verify | |
