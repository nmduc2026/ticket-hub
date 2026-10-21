# Việc còn treo: Sprint 1–4

Mọi việc treo của Sprint 1–4 nằm ở file này. Xoá dòng khi xong. Mã dòng có dạng `S<sprint>-<loại><số>`: `U` là việc cần người dùng, `V` là chưa kiểm chứng, `T` là việc kỹ thuật. Việc treo được xử lý ở [sprint dọn dẹp](2026-10-21-sprint-cleanup-plan.md) trước Sprint 5. Cột "Vào plan" ghi hạng mục bao việc đó: `DD-n` là hạng mục n của sprint dọn dẹp, `S<n>-<m>` là hạng mục m của plan Sprint n (chỉ cho việc phụ thuộc tính năng của sprint đó).

Trạng thái Git: `feature/sprint-1` → `sprint-4` xếp chồng (nhánh sau tách từ nhánh trước), chưa nhánh nào được push hay merge vào `main`.

## Việc chặn chung (làm theo thứ tự này)

| # | Việc chặn | Bạn làm | Mở khoá |
|---|---|---|---|
| B1 | Đưa code lên GitHub | Duyệt push 4 nhánh, mở PR theo thứ tự Sprint 1 → 4, xem CI lần đầu; bật bảo vệ `main`; cài Renovate, SonarCloud, `NVD_API_KEY` | S1-U1, U2, U3, U6 · S2-U4 · S3-U2, S3-T8, T9 |
| B2 | Tài khoản Stripe test (Singapore) | Tạo tài khoản, đặt `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `VITE_STRIPE_PUBLISHABLE_KEY`; đăng ký event `payment_intent.*`, `charge.refunded`, `refund.failed`. Bật luôn Connect cho spike Sprint 5 (hạng mục 16) | S3-U1, U3, V1–V5 · S4-U4, V1, V2 |
| B3 | Cụm k3d trên máy staging | Cài k3d theo `docs/ops/k8s-staging.md`, duyệt các lệnh `kubectl` / `helm` | S3-U4, U5, V6 · S4-V4–V9 |
| B4 | Object storage cho backup Postgres | Chọn phương án trong `docs/adr/0033-backup-object-storage.md` (đề xuất Garage) | S4-U1, V4 |
| B5 | Telegram bot | BotFather → `/newbot`, cho token và chat id (đặt vào Secret, không commit) | S4-U2, V7 |
| B6 | Sentry | Tạo tài khoản free, cho DSN | S4-U3, V9 |
| B7 | AWS | Checklist bảo vệ tài khoản, chạy Terraform thăm dò, lab 1 | S1-U4, U5 · S2-U3 |
| B8 | Duyệt tài liệu và quyết định | Đọc rồi báo chỗ cần đổi | S2-U1, U2 · S3-U6 · S4-U6 |
| B9 | Điện thoại + HTTPS | Thử app check-in trên điện thoại thật | S4-V3 |

---

## Sprint 1

### Cần người dùng

| # | Việc | Cách làm |
|---|---|---|
| S1-U1 | Push `feature/sprint-1`, mở PR, xem CI chạy lần đầu. Chưa kiểm chứng: AC-7 (CI trên PR), CI xanh trên `main`, image có trên GHCR | Duyệt rồi ra lệnh push; CI đỏ thì sửa theo log |
| S1-U2 | Cấu hình SonarCloud (chưa có thì bước Sonar tự bỏ qua) | `docs/ops/ci-cd.md` mục "Cần cấu hình một lần" |
| S1-U3 | Cài GitHub App Renovate | Cấu hình đã có ở `renovate.json` |
| S1-U4 | Checklist bảo vệ tài khoản AWS | `docs/ops/aws-account-protection.md` (MFA, Identity Center, budget 0,01$) |
| S1-U5 | Terraform thăm dò từng dịch vụ (AC-8); `terraform apply` luôn hỏi từng lần | `infra/terraform/aws/probe/README.md`; kết quả vào `docs/ops/aws-free-plan-probe.md` |
| S1-U6 | Bảo vệ nhánh `main`: bắt buộc job `backend`, `frontend`, `e2e`, `secrets` xanh, squash merge | Cài đặt repo |

### Việc kỹ thuật

| # | Việc | Ghi chú | Vào plan |
|---|---|---|---|
| S1-T1 | Hồ sơ cá nhân (#19) thiếu ảnh đại diện, số điện thoại, ngày sinh, thành phố, tài khoản liên kết, danh sách phiên, xoá tài khoản | `user_profiles` chưa có cột và API; hiện đổi email / mật khẩu / phiên dẫn sang Keycloak. Cần quyết định thêm trường hay để Keycloak giữ | DD-6 |
| S1-T2 | Chạy `app`, `gateway`, `frontend` dạng container trong compose `core` | Cần chốt hostname Keycloak (`localhost:8180` cho trình duyệt, `keycloak:8080` cho container) | bỏ (quyết định A9) |
| S1-T3 | Nâng Orval 8 | Cần Node ≥ 22.18 (máy dev Node 20) | DD-3 nếu nâng Node 22 |
| S1-T4 | Spring Cloud 2025.1.3 (build cho Boot 4.0.x) đang chạy trên Boot 4.1.1 | Nâng khi có release train cho Boot 4.1 | Renovate |
| S1-T5 | Ghi đè `tomcat` 11.0.27, `jackson-bom` 3.1.8 trong `backend/pom.xml` vì CVE | Bỏ khi Spring Boot cập nhật | Renovate |
| S1-T6 | npm 10.8 ở máy dev lỗi peer dependency của Vite 8 | Dùng `npx -y npm@11 install`; Dockerfile và CI đã dùng npm 11 | — |
| S1-T7 | OpenAPI do test backend ghi ra `frontend/openapi/openapi.json` | Đổi API thì commit file này; CI kiểm lệch | — (quy ước) |
| S1-T8 | Layout khách còn thiếu: ô tìm kiếm, link "Bán vé cùng Ticket Hub", tab danh mục | Thêm khi trang tương ứng có | DD-10 |

---

## Sprint 2

### Cần người dùng

| # | Việc | Cách làm |
|---|---|---|
| S2-U1 | Duyệt `docs/design/seat-map.md` (luồng lỗi, polling, bàn phím) | Đọc và báo chỗ cần đổi |
| S2-U2 | Review cuối Sprint 2 (hạng mục 17), kèm subagent review SQL giữ ghế; verify đã có ở `docs/test/sprint-2-verify.md` | Ra lệnh gọi review |
| S2-U3 | Chạy lab AWS 1; cần S1-U4, U5 trước | `infra/terraform/aws/lab1/README.md`; xong thì `destroy` và `verify-clean.sh` |
| S2-U4 | Push `feature/sprint-2`, mở PR | Duyệt rồi ra lệnh |

### Việc kỹ thuật

| # | Việc | Ghi chú | Vào plan |
|---|---|---|---|
| S2-T1 | `Idempotency-Key` chưa có trong OpenAPI của `POST /bookings` | FE truyền header tay trong `useHoldSeats`; khai `@Parameter` ở backend rồi sinh lại | DD-3 |
| S2-T2 | `PENDING_BOOKING_EXISTS` chưa kèm `bookingId` | Khách không có link quay lại đơn đang giữ; thêm `bookingId` vào Problem Details | DD-3 |
| S2-T3 | Bộ lọc facet chưa có "Tuỳ chọn khoảng ngày" | README §3; thêm khi có màn cần | S6-11 (đơn hàng organizer lọc ngày) |
| S2-T4 | Breadcrumb Admin / Organizer chỉ 2 cấp | Prototype #13, #24 có 4 cấp | DD-10 |
| S2-T5 | Trang sửa sự kiện chưa có: thẻ, độ tuổi, quy định, giờ mở cửa, công khai / chỉ link, kết thúc bán, xem trước | API không có trường tương ứng; không tự thêm | DD-7 |
| S2-T6 | Tab "Phòng chờ" ở trang sửa sự kiện | | S7-9 |
| S2-T7 | Link "Kết nối Stripe" trong hộp "Chưa thể publish" | | S6-10 |
| S2-T8 | Upload banner trên lab AWS: app còn trỏ S3 giả | Cần bucket thật hoặc S3Mock trên EC2 | S6-8 (lab 2) |
| S2-T9 | E2E trên stack thật cho giữ ghế và publish | Hiện chỉ có `*.mock.spec.ts` | DD-11 |
| S2-T10 | Thêm `.gitattributes` (`* text=auto eol=lf`); `spotless:check` đỏ ở máy Windows vì CRLF | CI Linux không gặp | DD-3 |
| S2-T11 | Sơ đồ ghế chưa vẽ khu `IMPORTED` và khu xoay (`sectionRect` bỏ qua `rotation`) | | S6-16, 18 |
| S2-T12 | Ghế ~6 px ở 390px, phải zoom mới chọn được | Cân nhắc phóng sẵn hoặc vùng chạm lớn hơn | DD-10 |

---

## Sprint 3

### Cần người dùng

| # | Việc | Cách làm |
|---|---|---|
| S3-U1 | Stripe test (B2): `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` vào `.env`, `VITE_STRIPE_PUBLISHABLE_KEY` cho FE | Dashboard Stripe → Developers → API keys; `stripe listen` cho webhook secret |
| S3-U2 | Rebase các nhánh lên `main` sau khi PR trước được squash merge | `git rebase --onto main <commit-cuối-sprint-trước> feature/sprint-N` |
| S3-U3 | Chạy `frontend/e2e/purchase.spec.ts` (`E2E_STRIPE=1`, `stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe`, `app` có `STRIPE_*` và profile `seed`) | Sau S3-U1 |
| S3-U4 | Dựng k3d, tạo Secret thật, kiểm app khởi động, probe, đăng nhập | `docs/ops/k8s-staging.md` |
| S3-U5 | Cài ArgoCD, `kubectl apply root.yaml`, hosts `*.tickethub.localhost`, tin CA tự ký | `docs/ops/gitops.md`, báo trước |
| S3-U6 | Duyệt `docs/design/checkout.md` (chỉ hiện email, bỏ họ tên / SĐT; tab vé và lọc đơn chỉ ở client) | Đọc và báo |
| S3-U7 | Cho chạy review độc lập phần thanh toán (`PaymentServiceImpl`, `PaymentSettlementServiceImpl`, `PaymentRefundServiceImpl`, `PaymentReconciliationServiceImpl`) như review Sprint 3 đề nghị | Ra lệnh |

### Chưa kiểm chứng

| # | Việc | Cần |
|---|---|---|
| S3-V1 | `StripeGateway` trên stripe-mock thật (mới chạy trên WireMock) | stripe-mock trong compose (S3-T2) |
| S3-V2 | Checkout trên trình duyệt thật: bố cục so với prototype #6 / #7, bàn phím, 390px, thẻ 4242 | S3-U1 |
| S3-V3 | Payment Element thật (component test đang mock `@stripe/react-stripe-js`); selector iframe Stripe của E2E | S3-U1 |
| S3-V4 | Đường webhook trong `stripe listen` khớp đường của controller | S3-U1 |
| S3-V5 | Truy vấn ghế trùng chạy bằng `docker compose exec postgres psql` khi compose `core` đang chạy | Docker |
| S3-V6 | Version chart Envoy Gateway v1.5.1, cert-manager v1.18.2, ArgoCD v3.1.5; `kubectl --dry-run=server` cho YAML (mới kiểm cú pháp) | Cụm (B3) |

### Việc kỹ thuật

| # | Việc | Ghi chú | Vào plan |
|---|---|---|---|
| S3-T1 | `.env.example` thiếu `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `TICKET_SIGNING_JWK`, `MAIL_*` và chưa truyền vào `app` khi chạy compose; chưa có `frontend/.env.example` | `TICKET_SIGNING_JWK` trống thì Ticket sinh khoá tạm, vé cũ không xác minh được sau khi khởi động lại | DD-3 |
| S3-T2 | Thêm service stripe-mock vào compose | | DD-3 (dev), S5-1 (staging) |
| S3-T3 | `booking` còn dùng JdbcTemplate | Chuyển sang entity + native query khi bạn muốn; đã có test 50 luồng làm lưới an toàn | DD-4 |
| S3-T4 | `shared/idempotency`, `shared/storage` chưa chia `config` / `util` | Chia nếu muốn đồng nhất | giữ nguyên |
| S3-T5 | Đơn `EXPIRED` / `CANCELLED` nhận thanh toán muộn: FE chỉ chờ, chưa nói "không giữ lại được ghế, sẽ hoàn tiền" | Cần API báo `BookingConfirmationFailed` / hoàn tiền cho khách | DD-5 |
| S3-T6 | Mở lại trang thanh toán sau khi Stripe đã nhận tiền mà chưa `CONFIRMED` vẫn hiện form | Cần `GET` trạng thái payment | DD-5 |
| S3-T7 | "Vé của tôi" và "Đơn của tôi": tab / tìm / lọc ở client; "Tải PDF", "Thêm vào lịch", hoá đơn đang ẩn | API chưa có lọc | DD-10 |
| S3-T8 | Image GHCR chỉ có sau khi `main` chạy CI; trước đó cụm báo `ImagePullBackOff` | Sau B1 | DD-1, DD-2 |
| S3-T9 | Job `update-image-tags` push thẳng `main`, bị chặn nếu bắt buộc PR | Cho github-actions bỏ qua hoặc đổi sang PR tự động | DD-2 |
| S3-T10 | Gateway trong cụm phải phân giải `auth.tickethub.localhost` (CoreDNS rewrite về Envoy Gateway) | Kiểm khi dựng cụm | DD-13 |

---

## Sprint 4

Trạng thái: hạng mục 1–8b và 12–13 xong (`./mvnw verify` 327 test, `vitest` 128 test, Playwright mock xanh; review nội tuyến APPROVED, xem cuối `docs/test/sprint-4-verify.md`). Hạng mục 9, 10, 11 đã viết manifest / code nhưng **chưa chạy trên cụm**.

### Cần người dùng

| # | Việc | Cách làm |
|---|---|---|
| S4-U1 | Chọn object storage cho backup Postgres (B4) | `docs/adr/0033-backup-object-storage.md`; Claude viết manifest dựng storage sau khi chọn |
| S4-U2 | Telegram bot token + chat id (B5) | Đặt vào Secret; `chat_id: 0` trong `kube-prometheus-stack.yaml` là giữ chỗ |
| S4-U3 | Sentry DSN (B6) | Biến `VITE_SENTRY_DSN`, không commit |
| S4-U4 | Stripe test key (trùng S3-U1) | Xem S3-U1 |
| S4-U5 | Đăng ký event `charge.refunded`, `refund.failed` cho endpoint webhook | Dashboard Stripe → Webhooks, hoặc `stripe listen --events payment_intent.*,charge.refunded,refund.failed` |
| S4-U6 | Duyệt các quyết định nhỏ Sprint 4 (danh sách cuối file) | Đọc và báo |

### Chưa kiểm chứng

| # | Việc | Cần |
|---|---|---|
| S4-V1 | Hoàn tiền với Stripe thật: thẻ `4000 0000 0000 5126` ra `refund.failed`; payload `charge.refunded` / `refund.failed` (`payment_intent`, `failure_reason`) đang theo tài liệu | B2 + `stripe listen` |
| S4-V2 | Số tiền 950.000 gửi Stripe (mới kiểm bằng WireMock) | B2 |
| S4-V3 | Quét QR bằng camera thật, âm thanh, rung; Safari iOS (cần HTTPS) | B9 |
| S4-V4 | CNPG, WAL archive, base backup; PITR (C7: RTO ≤ 15 phút, mất ≤ 5 phút); kill primary (C1: < 30 giây). Đổi tên Service / cổng giữ chỗ `backup-s3.tickethub.svc:3900` theo B4 | B3, B4 |
| S4-V5 | `kubectl --dry-run=server` cho `postgres/cluster.yaml` và 2 Application ArgoCD; chart `cloudnative-pg` 0.26.0 | B3 |
| S4-V6 | `helm template` / dry-run cho `kube-prometheus-stack` 75.15.1, `prometheus-blackbox-exporter` 11.1.1 (version theo trí nhớ); `promtool check rules` | B3 hoặc `helm search repo` + `promtool` |
| S4-V7 | Tên metric (`tickethub_*_total`, `hikaricp_*`, `http_server_requests_seconds_*`), nhãn `job="app"`, label ServiceMonitor so với `/actuator/prometheus` thật; alert giả lập tới Telegram (AC-12), cert 1 giờ tự gia hạn (C9), alert burn rate | B3, B5 |
| S4-V8 | Trace Gateway → app trong Jaeger (AC-13): chưa chứng minh Gateway (MVC proxy) tự gắn `traceparent`; image `jaegertracing/jaeger:2.9.0` và cờ OTLP | B3; Gateway không gắn thì thêm propagation ở proxy |
| S4-V9 | Lỗi JS giả lập trong Sentry (AC-13) | B6 |

### Việc kỹ thuật

| # | Việc | Ghi chú | Vào plan |
|---|---|---|---|
| S4-T1 | Chạy lại hạng mục 9, 10, 11 trên cụm | Sau B3–B6 | DD-13, DD-14 |
| S4-T2 | Wave ArgoCD: CRD PodMonitor chưa có thì sync CNPG lỗi, phải sync lại | Kiểm trên cụm | DD-13 |
| S4-T3 | PgBouncer (`Pooler`) cho CNPG | | S5-12 |
| S4-T5 | Thông báo `SALE_PAUSED` và `HOLD_EXPIRING` mới có cài đặt, chưa có nơi phát sinh | | `HOLD_EXPIRING`: DD-8; `SALE_PAUSED`: S6-4 |
| S4-T6 | Thông báo cho Organizer / Admin và SSE `/me/notifications/stream` | Admin: S5-9, S6-7; SSE: S7-4. Organizer nhận "khách yêu cầu hoàn": chưa có | Organizer: DD-8; Admin: S5-9, S6-7; SSE: S7-4 |
| S4-T7 | Gửi email theo cờ `email` của loại thông báo tuỳ chọn (cờ mới chỉ lưu) | | DD-8 |
| S4-T8 | Huỷ sự kiện và hoàn hàng loạt (BR-08) chưa có producer `RefundRequested(EVENT_CANCELLED)` | | S6-5 |
| S4-T9 | `TicketCheckedIn` chưa phát; check-in chưa chặn theo thời gian sự kiện | Phát event: S6-6; chặn theo thời gian: chưa có | DD-9 |
| S4-T10 | `RepublishIncompleteEvents` gửi lại event lỗi không giới hạn số lần, chưa có DLT | | S8-6 |
| S4-T11 | NetworkPolicy `allow-monitoring-ingress` cho `/actuator/prometheus` chưa chạy trên cụm | B3 | DD-14 |
| S4-T12 | Admin hoàn thủ công không bắt `Idempotency-Key` | Đã chặn bằng trạng thái, gọi lần hai trả 409; giữ nguyên trừ khi bạn muốn đổi | — |

### Quyết định nhỏ Sprint 4 (chờ duyệt, S4-U6)

- `RefundRequested` mang `reason` và `refundAmountMinor`; Payment tự tính lại số tiền từ payment đã thu (khách yêu cầu: trừ `application_fee_minor`; lý do khác: hoàn 100%) và chỉ cảnh báo khi lệch.
- Nhả ghế sau hoàn đếm `TicketRevoked` theo từng vé trong `booking.revoked_tickets`; yêu cầu hoàn đến trước khi vé kịp phát thì vé sinh ra đã `REVOKED` (`ticket.revoked_bookings`).
- Phát vé gặp ghế đã có vé chưa thu hồi: không nhả ghế của booking đó, hoàn 100% gồm phí.
- `GET /me/bookings` thêm `ticketCount`, `platformFeeMinor`, `refundableAmountMinor`, `refundDeadline` để FE không tự tính tiền.
- Metric `tickethub.booking.held` (không phải `created`) vì Prometheus coi hậu tố `_created` là dành riêng.
- Màn `/admin/orders` hiện chỉ có danh sách `REFUND_FAILED`; các bộ lọc khác của #29 làm ở S6-12.
- FE check-in chưa có số đếm "312/1.200" và tên người mua vì API chưa trả (`docs/design/checkin.md`); số đếm có ở S6-6.
- Job gửi lại event treo tắt trong test (`tickethub.events.republish-interval=24h`); test gọi `IncompleteEventPublications` trực tiếp.
