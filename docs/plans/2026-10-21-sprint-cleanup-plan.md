# Plan: Sprint dọn dẹp (giữa Sprint 4 và Sprint 5)

**Mục tiêu:** xử lý hết việc treo của Sprint 1–4 trước khi mở Sprint 5: code lên `main` qua PR với CI xanh, trả các nợ kỹ thuật đã chốt, chạy thật những gì mới kiểm bằng mock (Stripe, k3d, observability). File này là nơi duy nhất ghi việc treo của Sprint 1–4; xong việc nào thì đánh dấu ở bảng hạng mục.

**Đầu vào:** báo cáo verify `docs/test/sprint-1…4-verify.md`; review Sprint 3 `docs/plans/2026-10-19-sprint-3-review.md`; quyết định của người dùng ngày 21/10/2026 (mục "Quyết định đã chốt"). Mỗi hạng mục chỉ đọc đúng các § `overview.md` và ADR ở cột "Đọc".

**Hiện trạng:** `feature/sprint-1` → `sprint-4` xếp chồng (nhánh sau tách từ nhánh trước), chưa nhánh nào được push hay merge vào `main`. Sprint 4: hạng mục 9 (CNPG), 10 (Prometheus / alert), 11 (tracing / Sentry) đã viết manifest và code nhưng chưa chạy trên cụm.

**Ngoài sprint này:** việc phụ thuộc tính năng của sprint sau, đã ghi vào plan sprint đó (mục "Việc chuyển sang sprint sau" cuối file); chia lại package `shared` (giữ nguyên).

## Việc chặn cần bạn làm

Làm theo thứ tự này; mỗi việc mở khoá các hạng mục ở cột cuối.

| # | Việc chặn | Bạn làm | Mở khoá |
|---|---|---|---|
| B1 | Đưa code lên GitHub | Duyệt push 4 nhánh và mở PR theo thứ tự Sprint 1 → 4; bật bảo vệ `main` (bắt buộc job `backend`, `frontend`, `e2e`, `secrets` xanh, squash merge); cài GitHub App Renovate (cấu hình ở `renovate.json`); SonarCloud theo `docs/ops/ci-cd.md` mục "Cần cấu hình một lần"; xin `NVD_API_KEY` ở nvd.nist.gov/developers/request-an-api-key | 1, 2 |
| B2 | Tài khoản Stripe test (Singapore) | Dashboard Stripe → Developers → API keys: đặt `STRIPE_SECRET_KEY`, `VITE_STRIPE_PUBLISHABLE_KEY` (pk_test_…) vào `.env`; `stripe listen --events payment_intent.*,charge.refunded,refund.failed --forward-to localhost:8080/api/v1/webhooks/stripe` lấy `STRIPE_WEBHOOK_SECRET`. Bật luôn Connect cho spike Sprint 5 (hạng mục 16) | 12 |
| B3 | Cụm k3d trên máy staging | Cài k3d theo `docs/ops/k8s-staging.md`; duyệt từng lệnh `kubectl` / `helm` Claude báo trước | 13, 14, 15 |
| B4 | Telegram bot | BotFather → `/newbot`, cho token và chat id (đặt vào Secret, không commit; `chat_id: 0` trong `kube-prometheus-stack.yaml` đang là giữ chỗ) | 14 |
| B5 | Sentry | Tạo tài khoản free ở sentry.io, cho DSN (biến `VITE_SENTRY_DSN`, không commit) | 14 |
| B6 | AWS | Checklist `docs/ops/aws-account-protection.md` (MFA, Identity Center, budget 0,01$); duyệt `terraform apply` thăm dò và lab 1 (hỏi từng lần) | 16 |
| B7 | Duyệt tài liệu | Đọc `docs/design/seat-map.md`, `docs/design/checkout.md`, quyết định nhỏ Sprint 4 (cuối file này) và báo chỗ cần đổi | 17 |
| B8 | Điện thoại + HTTPS | Thử app check-in trên điện thoại thật (Safari iOS cần HTTPS) | 15 |

## Hạng mục

| # | Việc | Vùng | Đọc | Phụ thuộc | Xong khi |
|---|---|---|---|---|---|
| 1 | Đưa Sprint 1–4 lên `main`: push 4 nhánh, mở PR theo thứ tự, CI chạy lần đầu (chưa từng chạy thật: AC-7 Sprint 1 "CI trên PR", CI xanh trên `main`, image trên GHCR), sửa đỏ, squash merge, rebase nhánh sau lên `main` (`git rebase --onto main <commit-cuối-sprint-trước> feature/sprint-N`) | Infra | `docs/ops/ci-cd.md`; CLAUDE.md (Git) | B1 | 4 PR đã merge, CI xanh trên `main`, image 3 ứng dụng có trên GHCR |
| 2 | Job `update-image-tags` mở PR tự động thay vì push thẳng `main` (bị chặn khi bắt buộc PR), CI xanh thì tự merge | Infra | §19.3; skill infra | 1 | Merge một thay đổi vào `main` → bot mở PR đổi tag → tự merge → ArgoCD thấy tag mới |
| 3 | Nợ nhỏ: `.gitattributes` (`* text=auto eol=lf`) và chuẩn hoá xuống dòng để `spotless:check` xanh trên Windows; `infra/docker-compose/.env.example` đủ `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `TICKET_SIGNING_JWK`, `MAIL_*` và truyền vào `app` (thiếu `TICKET_SIGNING_JWK` thì Ticket sinh khoá tạm, vé cũ không xác minh được sau khi khởi động lại), thêm `frontend/.env.example`, service stripe-mock trong compose dev; khai `Idempotency-Key` bằng `@Parameter` trong OpenAPI của `POST /bookings` rồi sinh lại client, FE bỏ truyền header tay trong `useHoldSeats`; `PENDING_BOOKING_EXISTS` kèm `bookingId` + FE link về đơn đang giữ; nâng Orval 8 nếu máy dev đã lên Node ≥ 22.18 | BE, FE, Infra | §6.7, §11.1, §19.2 | 1 | `spotless:check` xanh trên máy dev; `docker compose --profile core up` chạy được với `.env` sinh từ `.env.example`; hàm Orval `createBooking` có tham số `idempotencyKey` |
| 4 | `booking` chuyển từ JdbcTemplate sang entity + Spring Data JPA; SQL đặc thù (`FOR UPDATE` theo thứ tự `seat_id`, `UPDATE … WHERE status = 'AVAILABLE'`, `RETURNING`, upsert `ON CONFLICT … WHERE`) giữ bằng native query; cột `version` cho optimistic lock theo convention | BE | §6.2, §10; ADR 0020, 0021; skill springbot (JPA) | 1 | Test 50 luồng tranh 1 ghế, test deadlock, truy vấn ghế trùng §10 và toàn bộ test `booking` xanh, số test không giảm |
| 5 | Thanh toán đến muộn và mở lại checkout: Booking ghi lại khi nhận `PaymentSucceeded` cho đơn `EXPIRED` / `CANCELLED` mà không giữ lại được ghế, cập nhật khi nhận `RefundCompleted` / `RefundFailed`; `GET /bookings/{id}` thêm `latePayment` (`REFUND_PENDING` / `REFUNDED` / `REFUND_FAILED`); `POST /payments` khi payment của đơn đã `SUCCEEDED` → `409 PAYMENT_ALREADY_SUCCEEDED`. FE checkout: hiện "Không giữ lại được ghế, tiền sẽ được hoàn" theo `latePayment`; nhận `PAYMENT_ALREADY_SUCCEEDED` → "Đang xác nhận thanh toán" và hỏi lại trạng thái booking. Ghi vào §7.3, §11.2 | BE, FE | §7.2, §7.3, §7.7, §11.2; ADR 0019 | 4 | Integration test cả hai nhánh; Playwright (mock): đơn hết hạn có `latePayment = REFUND_PENDING` hiện đúng thông báo; tải lại trang sau khi đã trả không hiện lại form |
| 6 | Hồ sơ cá nhân đầy đủ (#19): `user_profiles` thêm `avatar_url`, `phone`, `date_of_birth`, `province_id` (→ `administrative_units` cấp 1); `GET/PUT /me/profile`, `POST /me/avatar` (dùng lại cách upload banner); phiên đăng nhập `GET /me/sessions`, `DELETE /me/sessions/{id}` và xoá tài khoản `DELETE /me` qua Keycloak Admin API của module `account` (xoá user ở Keycloak, ẩn danh hoá `user_profiles`, giữ booking và vé cũ để đối soát; còn vé của sự kiện chưa diễn thì chặn). Email, tên, mật khẩu vẫn do Keycloak giữ. Không có mục "tài khoản liên kết" (đã gỡ khỏi prototype). Sửa §6.6, §11.2 | BE, FE | §6.6, §11.2, §13.2; ADR 0016; prototype #19 | 1 | Integration test (Testcontainers Keycloak): sửa hồ sơ, đăng xuất 1 phiên, xoá tài khoản; user khác không đọc / sửa được; Playwright #19 qua ở 1280px và 390px |
| 7 | Trường sự kiện còn thiếu (#24): `events` thêm `doors_open_at`, `age_restriction` (số tuổi tối thiểu, có thể trống), `rules` (văn bản); sửa được cả sau publish, bổ sung vào danh sách thông tin hiển thị của BR-14; hiện ở chi tiết sự kiện (#3) và vé (#9). Gỡ khỏi prototype #24: thẻ, chế độ chỉ-link, kết thúc bán, xem trước | BE, FE | §6.1, §11.2; BR-14; prototype #3, #9, #24 | 1 | Test: sửa 3 trường sau publish → 200, `EventUpdated` cập nhật `event_snapshots`; `doors_open_at` sau `starts_at` → 422 |
| 8 | Thông báo còn thiếu: organizer nhận "khách yêu cầu hoàn"; gửi email theo cờ `email` của loại tuỳ chọn trong `notification_preferences` (cờ hiện mới chỉ lưu); job ShedLock "sắp hết giờ giữ ghế" (`HOLD_EXPIRING`, hiện mới có cài đặt): booking `PENDING_PAYMENT` chưa bắt đầu thanh toán còn ≤ 2 phút → 1 thông báo | BE | §6.5; BR-03 | 1 | Integration test: mỗi loại tạo đúng 1 thông báo, gửi lại event không trùng; tắt cờ `email` → không gửi email, vẫn có thông báo trong app |
| 9 | Check-in theo giờ sự kiện: chỉ nhận từ 3 giờ trước `starts_at` tới `ends_at`, ngoài khoảng → `422 CHECKIN_NOT_OPEN` / `EVENT_ENDED`; phát `TicketCheckedIn` (có trong §8.1, chưa phát). Ghi quy tắc vào BR-09 và §7.5; FE check-in hiện đúng thông báo | BE, FE | §6.4, §7.5, §8.1; BR-09; ADR 0024 | 1 | Test biên: đúng 3 giờ trước → nhận, sớm hơn 1 giây → `CHECKIN_NOT_OPEN`; sau `ends_at` → `EVENT_ENDED` |
| 10 | FE nhỏ: chạm vào khu trên sơ đồ ghế ở màn hẹp thì zoom vào khu đó rồi mới chọn ghế (ghế hiện ~6 px ở 390px); "Thêm vào lịch" ở vé (#9) bằng file `.ics` sinh ở FE, gỡ "Tải PDF" và hoá đơn khỏi prototype, giữ lọc vé / đơn ở client; breadcrumb 4 cấp `Trang chủ › Sự kiện › <tên> › <tab>` ở trang sửa sự kiện (#13, #24); layout khách thêm ô tìm kiếm, link "Bán vé cùng Ticket Hub", tab danh mục | FE | §14.4; prototype #1, #5, #9, #13, #24 | 1 | Playwright 390px: chạm khu → zoom → chọn được ghế; file `.ics` có giờ theo `Asia/Ho_Chi_Minh`; component test breadcrumb |
| 11 | E2E trên stack thật (compose `core`, không cần Stripe) cho giữ ghế và publish; hiện chỉ có `*.mock.spec.ts` | FE, Verify | §20.1; skill testcase-rule | 3 | Playwright chạy với `app` + `gateway` thật trong CI |
| 12 | Chạy thật với Stripe test: `StripeGateway` trên stripe-mock (mới chạy trên WireMock); checkout trên trình duyệt thật (bố cục so với prototype #6 / #7, bàn phím, 390px, thẻ 4242, Payment Element thật, selector iframe của E2E); `frontend/e2e/purchase.spec.ts` với `E2E_STRIPE=1`, `app` có `STRIPE_*` và profile `seed`, kiểm đường webhook của `stripe listen` khớp controller; hoàn tiền thật (thẻ `4000 0000 0000 5126` → `refund.failed`, số tiền 950.000, payload `charge.refunded` / `refund.failed` có `payment_intent`, `failure_reason`); truy vấn ghế trùng bằng `docker compose exec postgres psql` | Verify | §7.2, §7.4; ADR 0022 | 3, B2 | Ghi kết quả vào `docs/test/sprint-cleanup-verify.md`; payload thật khác giả định thì sửa code và test |
| 13 | Dựng k3d trên máy staging và chạy phần hạ tầng đã viết: Secret thật, app khởi động, probe, đăng nhập; ArgoCD app-of-apps (`kubectl apply root.yaml`, hosts `*.tickethub.localhost`, tin CA tự ký); CoreDNS rewrite `auth.tickethub.localhost` về Envoy Gateway; xác nhận version chart Envoy Gateway v1.5.1, cert-manager v1.18.2, ArgoCD v3.1.5, `cloudnative-pg` 0.26.0 và `kubectl --dry-run=server` cho YAML; Garage (ADR 0033) làm object storage, đổi Service / cổng giữ chỗ `backup-s3.tickethub.svc:3900` trong `postgres/cluster.yaml`; CNPG + WAL archive + base backup (sync lại nếu wave ArgoCD chạy trước khi có CRD PodMonitor); diễn tập PITR (C7) và kill primary (C1) | Infra | §18, §19.4, §20.3 (C1, C7); ADR 0033; `docs/ops/k8s-staging.md`, `docs/ops/gitops.md` | 1, 2, B3 | App chạy trên k3d qua ArgoCD; PITR ≤ 15 phút, mất ≤ 5 phút; failover < 30 giây |
| 14 | Observability trên cụm: `helm template` / dry-run cho `kube-prometheus-stack` 75.15.1, `prometheus-blackbox-exporter` 11.1.1 (version theo trí nhớ); `promtool check rules`; đối chiếu tên metric (`tickethub_*_total`, `hikaricp_*`, `http_server_requests_seconds_*`), nhãn `job="app"`, label ServiceMonitor với `/actuator/prometheus` thật; alert giả lập tới Telegram, alert burn rate, cert 1 giờ tự gia hạn (C9); trace Gateway → app trong Jaeger (chưa chứng minh Gateway tự gắn `traceparent`, không gắn thì thêm propagation ở proxy; kiểm image `jaegertracing/jaeger:2.9.0` và cờ OTLP); lỗi JS trong Sentry; NetworkPolicy `allow-monitoring-ingress` | Infra | §16; `docs/ops/observability.md` | 13, B4, B5 | AC-12, AC-13 của Sprint 4 có bằng chứng chạy thật |
| 15 | Check-in trên điện thoại thật qua HTTPS: camera, âm thanh, rung, Safari iOS | Verify | §7.5 | 9, 13, B8 | Quét được vé thật; ghi kết quả vào báo cáo verify |
| 16 | AWS: Terraform thăm dò Free plan từng dịch vụ (`infra/terraform/aws/probe/README.md`, kết quả vào `docs/ops/aws-free-plan-probe.md`, AC-8 Sprint 1); lab 1 (`infra/terraform/aws/lab1/README.md`) rồi `destroy` + `verify-clean.sh` | Infra | §23; `infra/terraform/aws/` | 1, B6 | `docs/ops/aws-free-plan-probe.md` đủ bảng; lab 1 chạy được và sạch sau destroy |
| 17 | Sửa tài liệu theo góp ý của bạn ở B7: `docs/design/seat-map.md` (luồng lỗi, polling, bàn phím), `docs/design/checkout.md` (chỉ hiện email, bỏ họ tên / SĐT; tab vé và lọc đơn chỉ ở client), quyết định nhỏ Sprint 4 | Docs | — | B7 | Bạn đã duyệt cả 3 |
| 18 | Review còn thiếu: review cuối Sprint 2 kèm review độc lập SQL giữ ghế; review độc lập phần thanh toán Sprint 3 (`PaymentServiceImpl`, `PaymentSettlementServiceImpl`, `PaymentRefundServiceImpl`, `PaymentReconciliationServiceImpl`); chạy sau hạng mục 4 để review luôn bản JPA | Review | skill `code-review`, `tech-lead` | 4, 5 | Hai báo cáo review; phát hiện Critical / High đã sửa |
| 19 | Verify toàn sprint và review cuối sprint | Verify, Review | AC bên dưới | 1–18 | `docs/test/sprint-cleanup-verify.md`; APPROVED |

## Thứ tự

1. 1 trước tiên (mọi hạng mục khác merge qua PR); 2, 3 ngay sau.
2. Code: 4 → 5; 6, 7, 8, 9, 10 song song sau 1; 11 sau 3. Hạng mục 4 xong trước Sprint 5 vì Sprint 5 sửa đúng lớp giữ ghế.
3. Hạng mục chờ bạn (12–17) làm ngay khi việc chặn tương ứng được gỡ, không chờ phần code.
4. 18 sau 4 và 5; 19 cuối cùng.

## API cho FE mock

| Endpoint | Request chính | Response chính | Mã lỗi nghiệp vụ |
|---|---|---|---|
| `GET /bookings/{id}` | — | thêm `latePayment?: REFUND_PENDING \| REFUNDED \| REFUND_FAILED` | — |
| `POST /payments` | — | — | thêm `409 PAYMENT_ALREADY_SUCCEEDED` |
| `POST /bookings` | — | — | `409 PENDING_BOOKING_EXISTS` kèm `bookingId` |
| `GET/PUT /me/profile` | `{phone, dateOfBirth, provinceId}` | thêm `avatarUrl`, `phone`, `dateOfBirth`, `province` | `PHONE_INVALID` |
| `POST /me/avatar` | multipart `file` (jpg / png / webp, ≤ 2 MB) | `{avatarUrl}` | `FILE_TYPE_NOT_ALLOWED`, `FILE_TOO_LARGE` |
| `GET /me/sessions`, `DELETE /me/sessions/{id}` | — | `[{id, ipAddress, startedAt, lastAccessAt, current}]` | `CANNOT_END_CURRENT_SESSION` |
| `DELETE /me` | `{confirmEmail}` | `204` | `ACCOUNT_HAS_UPCOMING_TICKETS`, `CONFIRM_EMAIL_MISMATCH` |
| `PUT /organizer/events/{id}` | thêm `doorsOpenAt`, `ageRestriction`, `rules` | — | `DOORS_OPEN_AFTER_START` |
| `POST /checkins` | — | — | thêm `CHECKIN_NOT_OPEN`, `EVENT_ENDED` |

## Acceptance criteria

- AC-1: Sprint 1–4 đã merge vào `main` qua PR, CI xanh; image `app`, `gateway`, frontend có trên GHCR; cập nhật image tag đi qua PR tự động.
- AC-2: `spotless:check` xanh trên Windows; compose `core` chạy được chỉ với `.env` sinh từ `.env.example`.
- AC-3 (BR-01): Sau khi chuyển `booking` sang JPA: test 50 luồng tranh 1 ghế → đúng 1 thắng; hai booking chồng ghế không treo; truy vấn ghế trùng = 0; số test không giảm.
- AC-4 (§7.3): Đơn `EXPIRED` nhận thanh toán, không giữ lại được ghế → `GET /bookings/{id}` có `latePayment = REFUND_PENDING`, sau khi hoàn xong là `REFUNDED`; FE hiện đúng thông báo. Mở lại checkout của đơn đã trả → `409 PAYMENT_ALREADY_SUCCEEDED`, FE không hiện lại form.
- AC-5: Khách sửa SĐT, ngày sinh, tỉnh, ảnh đại diện; xem và đăng xuất từng phiên (không đăng xuất được phiên hiện tại bằng nút này); xoá tài khoản khi còn vé của sự kiện chưa diễn → `409 ACCOUNT_HAS_UPCOMING_TICKETS`.
- AC-6 (BR-14): Sau publish, sửa giờ mở cửa, độ tuổi, quy định → thành công và hiện ở trang sự kiện; trường bán vé vẫn bị khoá như trước.
- AC-7: Khách yêu cầu hoàn → organizer của sự kiện nhận 1 thông báo; booking còn ≤ 2 phút chưa thanh toán → khách nhận 1 thông báo "sắp hết giờ giữ ghế"; tắt cờ email của loại tuỳ chọn thì không nhận email loại đó.
- AC-8 (BR-09): Check-in sớm hơn 3 giờ trước giờ diễn → `CHECKIN_NOT_OPEN`; sau giờ kết thúc → `EVENT_ENDED`; check-in hợp lệ phát đúng 1 `TicketCheckedIn`.
- AC-9: Ở 390px chạm vào khu thì sơ đồ zoom vào khu, chọn được ghế; "Thêm vào lịch" tải file `.ics` đúng giờ diễn.
- AC-10: Luồng mua vé chạy trọn với Stripe test thật; hoàn tiền thật ra đúng số tiền; thẻ hoàn thất bại ra `REFUND_FAILED`.
- AC-11: App chạy trên k3d qua ArgoCD; PITR và failover đạt chỉ tiêu Sprint 4 (AC-10, AC-11 của Sprint 4).
- AC-12: Alert giả lập tới Telegram, trace Gateway → app trong Jaeger, lỗi JS trong Sentry (AC-12, AC-13 của Sprint 4).
- AC-13: Mọi hạng mục 1–18 xong hoặc được bạn đồng ý dời; không còn việc treo của Sprint 1–4 nằm ngoài file này và các plan Sprint 5–8.

## Thao tác cần báo trước

Push, mở PR, merge (hạng mục 1, 2 và mọi PR sau đó); `kubectl` / `helm` lên k3d; diễn tập PITR và kill primary trên staging; **`terraform apply` thăm dò và lab 1 (hỏi từng lần)**, `terraform destroy`; đặt Stripe key, Telegram token, Sentry DSN, `NVD_API_KEY` vào Secret / `.env` (không commit).

## Quyết định đã chốt (người dùng, 21/10/2026)

- Object storage cho backup: Garage (ADR 0033).
- Thanh toán đến muộn / mở lại checkout: dùng dữ liệu sẵn có của Booking và Payment, không thêm API trạng thái thanh toán riêng.
- Hồ sơ cá nhân: lưu thêm trường trong `user_profiles`; phiên và xoá tài khoản qua Keycloak Admin API; bỏ "tài khoản liên kết" (#19) và nút đăng nhập / đăng ký bằng Google (#18), cả ở prototype; chỉ đăng nhập bằng email.
- Trường sự kiện: chỉ thêm giờ mở cửa, độ tuổi, quy định.
- Thêm thông báo "khách yêu cầu hoàn" cho organizer và email theo cài đặt; check-in mở từ 3 giờ trước giờ diễn tới giờ kết thúc.
- Cập nhật image tag bằng PR tự động; không chạy `app` / `gateway` dạng container trong compose dev (container đã chạy trên k3d).
- Zoom theo khu ở màn hẹp; "Thêm vào lịch" bằng `.ics`; bỏ "Tải PDF" và hoá đơn.
- `booking` chuyển sang JPA; package `shared` giữ nguyên.

## Việc chuyển sang sprint sau

Phụ thuộc tính năng của sprint đó, đã ghi vào plan tương ứng:

| Việc | Plan |
|---|---|
| Bộ lọc facet "Tuỳ chọn khoảng ngày" | Sprint 6, hạng mục 11 |
| Link "Kết nối Stripe" trong hộp "Chưa thể publish" | Sprint 6, hạng mục 10 |
| Upload banner lên S3 thật trên AWS | Sprint 6, hạng mục 8 |
| Sơ đồ ghế khách vẽ đúng khu xoay và khu `IMPORTED` | Sprint 6, hạng mục 18 |
| Thông báo `SALE_PAUSED` | Sprint 6, hạng mục 4 |
| Thông báo vận hành cho Admin | Sprint 5, hạng mục 9; Sprint 6, hạng mục 7 |
| Huỷ sự kiện, hoàn hàng loạt (BR-08) | Sprint 6, hạng mục 5 |
| stripe-mock trên staging | Sprint 5, hạng mục 1 |
| PgBouncer (`Pooler`) | Sprint 5, hạng mục 12 |
| Tab "Phòng chờ" ở trang sửa sự kiện; SSE thông báo | Sprint 7, hạng mục 9, 4 |
| Giới hạn số lần gửi lại event lỗi (`RepublishIncompleteEvents`), DLT | Sprint 8, hạng mục 6 |

## Quy ước giữ nguyên (không phải việc)

- npm 10.8 ở máy dev lỗi peer dependency của Vite 8: dùng `npx -y npm@11 install` (Dockerfile và CI đã dùng npm 11).
- OpenAPI do test backend ghi ra `frontend/openapi/openapi.json`: đổi API thì commit file này, CI kiểm lệch.
- Spring Cloud 2025.1.3 chạy trên Boot 4.1.1; ghi đè `tomcat` 11.0.27, `jackson-bom` 3.1.8 vì CVE: Renovate nâng / bỏ khi có bản mới.
- Admin hoàn thủ công không bắt `Idempotency-Key`: đã chặn bằng trạng thái (`REFUND_FAILED` → `REFUND_REQUESTED`), gọi lần hai trả 409.

## Quyết định nhỏ Sprint 4 (chờ duyệt ở B7)

- `RefundRequested` mang `reason` và `refundAmountMinor`; Payment tự tính lại số tiền từ payment đã thu (khách yêu cầu: trừ `application_fee_minor`; lý do khác: hoàn 100%) và chỉ cảnh báo khi lệch.
- Nhả ghế sau hoàn đếm `TicketRevoked` theo từng vé trong `booking.revoked_tickets`; yêu cầu hoàn đến trước khi vé kịp phát thì vé sinh ra đã `REVOKED` (`ticket.revoked_bookings`).
- Phát vé gặp ghế đã có vé chưa thu hồi: không nhả ghế của booking đó, hoàn 100% gồm phí.
- `GET /me/bookings` thêm `ticketCount`, `platformFeeMinor`, `refundableAmountMinor`, `refundDeadline` để FE không tự tính tiền.
- Metric `tickethub.booking.held` (không phải `created`) vì Prometheus coi hậu tố `_created` là dành riêng.
- Màn `/admin/orders` hiện chỉ có danh sách `REFUND_FAILED`; các bộ lọc khác của #29 làm ở Sprint 6, hạng mục 12.
- FE check-in chưa có số đếm "312/1.200" và tên người mua vì API chưa trả (`docs/design/checkin.md`); số đếm có ở Sprint 6, hạng mục 6.
- Job gửi lại event treo tắt trong test (`tickethub.events.republish-interval=24h`); test gọi `IncompleteEventPublications` trực tiếp.
