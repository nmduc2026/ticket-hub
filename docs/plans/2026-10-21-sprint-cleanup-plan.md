# Plan: Sprint dọn dẹp (giữa Sprint 4 và Sprint 5)

**Mục tiêu:** xử lý hết việc treo của Sprint 1–4 trước khi mở Sprint 5: code lên `main` qua PR với CI xanh, trả các nợ kỹ thuật đã chốt, chạy thật những gì mới kiểm bằng mock (Stripe, k3d, observability). File này là nơi duy nhất ghi việc treo của Sprint 1–4; xong việc nào thì đánh dấu ở bảng hạng mục.

**Đầu vào:** báo cáo verify `docs/test/sprint-1…4-verify.md`; review Sprint 3 `docs/plans/2026-10-19-sprint-3-review.md`; quyết định của người dùng ngày 21/10/2026 (mục "Quyết định đã chốt"). Mỗi hạng mục chỉ đọc đúng các § `overview.md` và ADR ở cột "Đọc".

**Hiện trạng:** `feature/sprint-1` → `sprint-4` → `sprint-5` → `sprint-cleanup` → `sprint-6` → `sprint-7` xếp chồng (nhánh sau tách từ nhánh trước), chưa nhánh nào được push hay merge vào `main`. Hạng mục 4 và phần viết được của Sprint 5 đã làm trên `feature/sprint-5` (tách từ `sprint-4`, chưa push): xem mục "Tình trạng Sprint 5" cuối file. Sprint 4: hạng mục 9 (CNPG), 10 (Prometheus / alert), 11 (tracing / Sentry) đã viết manifest và code nhưng chưa chạy trên cụm.

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
| 3 | **Xong phần chạy ở local 22/10/2026 (nhánh `feature/sprint-cleanup`); còn: CI kiểm `spotless:check`, Orval 8 chờ Node ≥ 22.18.** Nợ nhỏ: `.gitattributes` (`* text=auto eol=lf`) và chuẩn hoá xuống dòng để `spotless:check` xanh trên Windows; `infra/docker-compose/.env.example` đủ `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `TICKET_SIGNING_JWK`, `MAIL_*` và truyền vào `app` (thiếu `TICKET_SIGNING_JWK` thì Ticket sinh khoá tạm, vé cũ không xác minh được sau khi khởi động lại), thêm `frontend/.env.example`, service stripe-mock trong compose dev; khai `Idempotency-Key` bằng `@Parameter` trong OpenAPI của `POST /bookings` rồi sinh lại client, FE bỏ truyền header tay trong `useHoldSeats`; `PENDING_BOOKING_EXISTS` kèm `bookingId` + FE link về đơn đang giữ; nâng Orval 8 nếu máy dev đã lên Node ≥ 22.18 | BE, FE, Infra | §6.7, §11.1, §19.2 | 1 | `spotless:check` xanh trên máy dev; `docker compose --profile core up` chạy được với `.env` sinh từ `.env.example`; hàm Orval `createBooking` có tham số `idempotencyKey` |
| 4 | **Xong 21/10/2026 (nhánh `feature/sprint-5`, commit 177e985; 328 test xanh, không đổi test).** `booking` chuyển từ JdbcTemplate sang entity + Spring Data JPA; SQL đặc thù (`FOR UPDATE` theo thứ tự `seat_id`, `UPDATE … WHERE status = 'AVAILABLE'`, `RETURNING`, upsert `ON CONFLICT … WHERE`) giữ bằng native query; cột `version` cho optimistic lock theo convention | BE | §6.2, §10; ADR 0020, 0021; skill springbot (JPA) | 1 | Test 50 luồng tranh 1 ghế, test deadlock, truy vấn ghế trùng §10 và toàn bộ test `booking` xanh, số test không giảm |
| 5 | **Xong 22/10/2026 (`feature/sprint-cleanup`).** Thanh toán đến muộn và mở lại checkout: Booking ghi lại khi nhận `PaymentSucceeded` cho đơn `EXPIRED` / `CANCELLED` mà không giữ lại được ghế, cập nhật khi nhận `RefundCompleted` / `RefundFailed`; `GET /bookings/{id}` thêm `latePayment` (`REFUND_PENDING` / `REFUNDED` / `REFUND_FAILED`); `POST /payments` khi payment của đơn đã `SUCCEEDED` → `409 PAYMENT_ALREADY_SUCCEEDED`. FE checkout: hiện "Không giữ lại được ghế, tiền sẽ được hoàn" theo `latePayment`; nhận `PAYMENT_ALREADY_SUCCEEDED` → "Đang xác nhận thanh toán" và hỏi lại trạng thái booking. Ghi vào §7.3, §11.2 | BE, FE | §7.2, §7.3, §7.7, §11.2; ADR 0019 | 4 | Integration test cả hai nhánh; Playwright (mock): đơn hết hạn có `latePayment = REFUND_PENDING` hiện đúng thông báo; tải lại trang sau khi đã trả không hiện lại form |
| 6 | **Xong 22/10/2026 (`feature/sprint-cleanup`); lỗi upload dùng mã sẵn có `UNSUPPORTED_IMAGE_TYPE` / `IMAGE_TOO_LARGE` thay cho `FILE_TYPE_NOT_ALLOWED` / `FILE_TOO_LARGE`.** Hồ sơ cá nhân đầy đủ (#19): `user_profiles` thêm `avatar_url`, `phone`, `date_of_birth`, `province_id` (→ `administrative_units` cấp 1); `GET/PUT /me/profile`, `POST /me/avatar` (dùng lại cách upload banner); phiên đăng nhập `GET /me/sessions`, `DELETE /me/sessions/{id}` và xoá tài khoản `DELETE /me` qua Keycloak Admin API của module `account` (xoá user ở Keycloak, ẩn danh hoá `user_profiles`, giữ booking và vé cũ để đối soát; còn vé của sự kiện chưa diễn thì chặn). Email, tên, mật khẩu vẫn do Keycloak giữ. Không có mục "tài khoản liên kết" (đã gỡ khỏi prototype). Sửa §6.6, §11.2 | BE, FE | §6.6, §11.2, §13.2; ADR 0016; prototype #19 | 1 | Integration test (Testcontainers Keycloak): sửa hồ sơ, đăng xuất 1 phiên, xoá tài khoản; user khác không đọc / sửa được; Playwright #19 qua ở 1280px và 390px |
| 7 | **Xong 22/10/2026 (`feature/sprint-cleanup`).** Trường sự kiện còn thiếu (#24): `events` thêm `doors_open_at`, `age_restriction` (số tuổi tối thiểu, có thể trống), `rules` (văn bản); sửa được cả sau publish, bổ sung vào danh sách thông tin hiển thị của BR-14; hiện ở chi tiết sự kiện (#3) và vé (#9). Gỡ khỏi prototype #24: thẻ, chế độ chỉ-link, kết thúc bán, xem trước | BE, FE | §6.1, §11.2; BR-14; prototype #3, #9, #24 | 1 | Test: sửa 3 trường sau publish → 200, `EventUpdated` cập nhật `event_snapshots`; `doors_open_at` sau `starts_at` → 422 |
| 8 | **Xong 22/10/2026 (`feature/sprint-cleanup`); thông báo organizer khi khách yêu cầu hoàn chỉ có trong app (payload không có email organizer).** Thông báo còn thiếu: organizer nhận "khách yêu cầu hoàn"; gửi email theo cờ `email` của loại tuỳ chọn trong `notification_preferences` (cờ hiện mới chỉ lưu); job ShedLock "sắp hết giờ giữ ghế" (`HOLD_EXPIRING`, hiện mới có cài đặt): booking `PENDING_PAYMENT` chưa bắt đầu thanh toán còn ≤ 2 phút → 1 thông báo | BE | §6.5; BR-03 | 1 | Integration test: mỗi loại tạo đúng 1 thông báo, gửi lại event không trùng; tắt cờ `email` → không gửi email, vẫn có thông báo trong app |
| 9 | **Xong 22/10/2026 (`feature/sprint-cleanup`).** Check-in theo giờ sự kiện: chỉ nhận từ 3 giờ trước `starts_at` tới `ends_at`, ngoài khoảng → `422 CHECKIN_NOT_OPEN` / `EVENT_ENDED`; phát `TicketCheckedIn` (có trong §8.1, chưa phát). Ghi quy tắc vào BR-09 và §7.5; FE check-in hiện đúng thông báo | BE, FE | §6.4, §7.5, §8.1; BR-09; ADR 0024 | 1 | Test biên: đúng 3 giờ trước → nhận, sớm hơn 1 giây → `CHECKIN_NOT_OPEN`; sau `ends_at` → `EVENT_ENDED` |
| 10 | **Xong 22/10/2026 (`feature/sprint-cleanup`).** FE nhỏ: chạm vào khu trên sơ đồ ghế ở màn hẹp thì zoom vào khu đó rồi mới chọn ghế (ghế hiện ~6 px ở 390px); "Thêm vào lịch" ở vé (#9) bằng file `.ics` sinh ở FE, gỡ "Tải PDF" và hoá đơn khỏi prototype, giữ lọc vé / đơn ở client; breadcrumb 4 cấp `Trang chủ › Sự kiện › <tên> › <tab>` ở trang sửa sự kiện (#13, #24); layout khách thêm ô tìm kiếm, link "Bán vé cùng Ticket Hub", tab danh mục | FE | §14.4; prototype #1, #5, #9, #13, #24 | 1 | Playwright 390px: chạm khu → zoom → chọn được ghế; file `.ics` có giờ theo `Asia/Ho_Chi_Minh`; component test breadcrumb |
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

## Tình trạng đợt code dọn dẹp (nhánh `feature/sprint-cleanup`, 22/10/2026)

Làm các hạng mục code không bị chặn (3 phần local, 5–10), mỗi hạng mục một commit, chưa push. `./mvnw verify`: 391 test, 1 lỗi ArchUnit (`EventUpdatedFactory` nằm trong `service/impl`) đã sửa ở commit cuối; frontend 165 test (một lần chạy có 1 test đỏ, 4 lần chạy lại không tái hiện được: theo dõi ở CI), Playwright mock (1280px, 390px) cho check-in, thanh toán đến muộn, hồ sơ #19, zoom theo khu đều xanh.

| Hạng mục | Kết quả | Còn lại |
|---|---|---|
| 9 Check-in theo giờ | `CHECKIN_NOT_OPEN` / `EVENT_ENDED` (khung cấu hình `tickethub.ticket.check-in-opens-before`), phát `TicketCheckedIn` | – |
| 7 Trường sự kiện | 3 trường ở Catalog, `event_snapshots`, trang #3, vé #9 (đọc bản hiện tại qua `CatalogApi.guestInfo`), form #24 | – |
| 5 Thanh toán đến muộn | `bookings.late_payment`, `latePayment` ở `GET /bookings/{id}`, `409 PAYMENT_ALREADY_SUCCEEDED`, FE checkout | Chạy với Stripe test thật ở hạng mục 12 |
| 8 Thông báo | Organizer nhận "khách yêu cầu hoàn" (trong app), `BookingHoldExpiring` + email theo cờ | – |
| 6 Hồ sơ #19 | Profile, avatar, phiên, xoá tài khoản (Keycloak thật trong test) | DC-1 |
| 10 FE nhỏ | Zoom theo khu, `.ics`, breadcrumb 4 cấp, header khách; gỡ nút hoá đơn ở prototype #7 | – |
| 3 Nợ nhỏ (local) | `.env.example` đủ biến, `frontend/.env.example`, stripe-mock (profile `core`), `Idempotency-Key` khai trong OpenAPI (Orval sinh tham số header), `PENDING_BOOKING_EXISTS` kèm `bookingId` | Orval 8 chờ Node ≥ 22.18; CI chạy `spotless:check` khi có hạng mục 1 |

### Việc treo của đợt code dọn dẹp

| # | Việc | Bị chặn bởi | Làm gì khi được gỡ |
|---|---|---|---|
| DC-1 | Kiểm `sid` trong access token qua Gateway thật (phiên hiện tại ở #19) và xoá tài khoản trên stack thật | Hạng mục 11 (E2E stack thật) | Đăng nhập 2 trình duyệt, đăng xuất phiên kia, xoá tài khoản rồi thử đăng nhập lại |
| DC-2 | Playwright stack thật cho các luồng mới (check-in theo giờ, thanh toán đến muộn, hồ sơ) | Hạng mục 11, 12 | Chuyển các `*.mock.spec.ts` mới sang chạy với `app` + `gateway` thật |

## Tình trạng Sprint 5 (nhánh `feature/sprint-5`, 21/10/2026)

Phần viết và kiểm được mà không cần cụm, tài khoản bên ngoài hay số đo trên staging đã làm xong, mỗi hạng mục một commit, chưa push. Backend 371 test, frontend 146 test đều xanh.

| Hạng mục Sprint 5 | Tình trạng | Còn thiếu (ghi ở "Việc treo" bên dưới) |
|---|---|---|
| 1 Khung load test | Đã viết `load-tests/jmeter/` (`ticket-hub.jmx` hợp lệ XML, seed user Keycloak, `seed-event.sql` đã chạy thử trên Postgres thật và chạy lại không lỗi) | Chưa chạy JMeter, chưa có stripe-mock trên staging | S5-1 |
| 2 Baseline | Chưa làm | Cần staging | S5-1 |
| 3 Lớp 1 Redis giữ ghế | Xong: Lua giữ nhóm / nhả compare-and-delete / gia hạn, TTL, nhả khi hết hạn - huỷ - xác nhận - TX lỗi; 12 test với Redis thật (50 luồng tranh 1 ghế, key mất vẫn không bán trùng) | Cờ `tickethub.booking.seat-hold.enabled` mặc định **tắt** để đo baseline; chưa đo hiệu năng | S5-1 |
| 4 Fail-closed | Xong: 503 `SEAT_HOLD_UNAVAILABLE` kèm `Retry-After`, probe dựng lại key khi Redis về hoặc mất dữ liệu, health `DEGRADED`, metric, alert `SeatHoldUnavailable`, runbook khung; test dừng Redis thật bằng `docker pause` | Alert chưa kiểm trên Prometheus thật; runbook chờ số đo diễn tập C4 | S5-3 |
| 5 Đo lại sau Redis | Chưa làm | Cần staging | S5-1 |
| 6 Organizer đăng ký / duyệt | Xong: `apply`, `application`, Admin list / approve / reject / bulk-approve; Account gán role qua Keycloak Admin API (idempotent); 16 test với Keycloak thật | Upload ảnh giấy phép / CCCD của form #22 chưa làm (xem câu hỏi) | Câu hỏi 1 |
| 7 Admin người dùng | Xong: tìm, lọc role, chi tiết (đơn, vé, hoàn tiền, sự kiện qua `BookingApi` / `TicketApi` / `CatalogApi`), khoá / mở khoá; 11 test với Keycloak thật (refresh token chết, đăng nhập bị từ chối) | – | – |
| 8 Audit | Xong: `audit_log` (module nào cũng ghi được, cùng transaction), Envers cho `organizers` và `user_profiles.enabled` kèm người thao tác; duyệt / từ chối / khoá / mở khoá / hoàn thủ công đều ghi | Envers cho `platform_fee_policies` chưa làm vì chưa có entity (Sprint 6 hạng mục phí); API đọc `GET /admin/audit-logs` thuộc Sprint 6 | Sprint 6 |
| 9 Thông báo vai trò | Xong: Admin nhận "chờ duyệt", organizer nhận kết quả trong app và email, idempotent; 4 test | – | – |
| 10 FE đăng ký organizer | Xong: 4 trạng thái, 6 test component (MSW) | Playwright "đăng ký → Admin duyệt → vào `/organizer`" | S5-5 |
| 11 FE Admin organizer / người dùng | Xong: lọc, side sheet, duyệt, từ chối kèm lý do, duyệt hàng loạt, khoá / mở khoá có xác nhận; 9 test component | Playwright "duyệt hàng loạt 3 hồ sơ", "khoá user → bị đăng xuất"; nút "Tạm khoá" của #15 để Sprint 6 (BR-13) | S5-5 |
| 12 Sentinel + PgBouncer | Đã viết: chart `core` có Sentinel (3 pod), kịch bản khởi động **đã diễn tập bằng 3 container: failover 6 giây, master cũ về lại làm replica**; Pooler CNPG, NetworkPolicy, `app` / `gateway` nối qua Sentinel, Flyway nối thẳng Postgres, ADR 0034 | Chưa chạy trên k3d; chưa đo `pg_stat_activity`; chưa xác nhận PgBouncer >= 1.21 | S5-2 |
| 13 ELK | Đã viết: ECK Elasticsearch / Kibana / Filebeat, ILM xoá sau 7 ngày, `docs/ops/logging.md` | Chưa chạy; version chart / stack theo trí nhớ; link Grafana và Jaeger sang Kibana chưa làm | S5-2 |
| 14 Chaos | Đã viết: Chaos Mesh (ArgoCD), thí nghiệm C2 / C4 (cả cụm, chỉ master) / C8 (script đầy disk), endpoint rò bộ nhớ profile `chaos` (ADMIN), `infra/chaos/README.md` | Chưa chạy, chưa có runbook và postmortem thật | S5-3 |
| 15 Soak / Stress | Chưa làm | Cần staging | S5-1 |
| 16 Spike Stripe Connect | Chưa làm | Cần B2 | S5-4 |
| 17, 18 Verify và review cuối sprint | Chưa làm | Làm sau khi các việc treo xong | S5-6 |

### Việc treo của Sprint 5

| # | Việc | Bị chặn bởi | Làm gì khi được gỡ | Xong khi |
|---|---|---|---|---|
| S5-1 | Chạy Flash sale, Load, Soak, Stress (hạng mục 1, 2, 5, 15) | B3; cài JMeter 5.6 (JDK 21) trên máy 16 GB, xác nhận hai máy cùng LAN; stripe-mock trên staging (hạng mục 3 dọn dẹp, Sprint 5 hạng mục 1); giới hạn CPU / RAM của `app`, `gateway` | Tạo 500 user + 1 sự kiện 100 ghế theo `load-tests/jmeter/README.md`; chạy lần 1 với `TICKETHUB_BOOKING_SEATHOLD_ENABLED=false` (baseline), lần 2 với `true`; ghi p95, số vé, truy vấn ghế trùng, deadlock; thêm phần thanh toán (webhook giả ký đúng) vào kịch bản; đo xong thì đổi mặc định của cờ sang `true` (để Sprint 7 load test chạy có lớp 1) | `docs/test/load/sprint-5-load.md` đủ bảng trước / sau; AC-1, AC-2, AC-14 |
| S5-2 | Chạy thật Sentinel, Pooler, ELK (hạng mục 12, 13) | B3, hạng mục 13 dọn dẹp (k3d + ArgoCD) | Đặt Secret `tickethub-redis`; xác nhận version chart `eck-operator` 3.1.0, Elastic Stack 9.1.4, PgBouncer >= 1.21 trong image Pooler; kill master Redis và kiểm giữ ghế + session; đo `pg_stat_activity` khi chạy Load; tìm log theo `traceId` trong Kibana; kiểm ILM bằng `_ilm/explain`; chốt ADR 0034: giữ `transaction` với `max_prepared_statements` > 0; còn lỗi prepared statement thì thử `prepareThreshold=0` của pgJDBC trước, vẫn lỗi mới đổi sang `session` | AC-6, AC-11 |
| S5-3 | Diễn tập C2, C4, C8 (hạng mục 14) | S5-2; hạng mục 14 dọn dẹp (Telegram, B4); stripe-mock trên staging | Áp từng file ở `infra/chaos/`, đo thời gian tới alert, viết runbook ở `docs/runbooks/` và postmortem ở `docs/postmortems/`; C4 chạy kèm Flash sale → 0 ghế trùng; C8 lấy heap dump và phân tích | AC-5, AC-12, AC-13 |
| S5-4 | Spike Stripe Connect (hạng mục 16) | B2 (tài khoản Stripe Singapore, bật Connect, webhook secret Connect) | Kiểm từng giả định ADR 0022: Express account, onboarding link, `account.updated`, destination charge VND, `reverse_transfer`, `refund_application_fee` | AC-15 |
| S5-5 | Playwright trên stack thật cho đăng ký / duyệt organizer và khoá user (hạng mục 10, 11) | Hạng mục 11 dọn dẹp (E2E trên stack thật) | Hai luồng: đăng ký → Admin duyệt → vào `/organizer`; khoá user → bị đăng xuất ở lần refresh kế tiếp | Hai luồng chạy được trong CI |
| S5-7 | Đối chiếu chữ trên giao diện #15, #22, #28 với prototype | Không bị chặn | Claude so chữ trong `frontend/src/features` với `docs/design/prototype/AdminOrganizers`, `OrganizerApply`, `AdminUsers`, liệt kê chỗ lệch; bạn duyệt rồi sửa | Danh sách chỗ lệch đã được bạn duyệt và sửa |
| S5-6 | Verify và review cuối Sprint 5 (hạng mục 17, 18) | S5-1 đến S5-5, S5-7 | `docs/test/sprint-5-verify.md`; review độc lập bằng subagent cho lớp Redis giữ ghế và khoá tài khoản | APPROVED |

Việc cần làm khi triển khai lên staging (thao tác báo trước, chưa làm): tạo Secret `tickethub-redis`; `app` cần `KEYCLOAK_ACCOUNT_ADMIN_CLIENT_SECRET` (đã khai trong `helm/app/values.yaml`, lấy từ Secret `tickethub-keycloak`); realm đã import trước đó phải có client `tickethub-account-admin` với `view-users`, `manage-users`.

### Quyết định Sprint 5 đã chốt (người dùng, 21/10/2026)

- Hồ sơ organizer bị từ chối được nộp lại và về `PENDING`; event `OrganizerRejected` báo organizer kèm lý do (đã có trong §8.1).
- Form hồ sơ theo prototype #22: loại hình (cá nhân / doanh nghiệp), tên hiển thị, tên pháp lý, mã số thuế (10 hoặc 13 số) hoặc CCCD (12 số), email, số điện thoại (`0…` hoặc `+84…`), website, giới thiệu, quy mô. Thêm cột vào `catalog.organizers`.
- **Không upload ảnh giấy phép / CCCD** ở form #22: ảnh CCCD là dữ liệu cá nhân nhạy cảm, giá trị học thêm ít. Admin duyệt dựa trên mã số thuế / CCCD đã nhập. Ghi chú ở `docs/design/README.md` (#22).
- Lớp Redis giữ ghế mặc định **tắt** để đo baseline, đổi mặc định sang bật sau khi đo xong (S5-1). Redis chết thì giữ ghế trả 503 và health là `DEGRADED` (HTTP 200) chứ không `DOWN`, để pod không rời vòng phục vụ.
- PgBouncer chạy `transaction` (ADR 0034, Proposed tới khi kiểm trên staging ở S5-2); Flyway nối thẳng Postgres.
- Test tích hợp của backend dựng thêm Keycloak (~1 GB RAM) và Redis trong context dùng chung, suite chậm thêm ~1 phút ở lần khởi động đầu: chấp nhận. Máy dev bật `testcontainers.reuse.enable=true` (trong `~/.testcontainers.properties`) để lần chạy sau không dựng lại.
- Khai báo schema 200 cho các endpoint mới trong OpenAPI (không khai thì Orval không sinh kiểu).
- Chữ trên giao diện #15, #22, #28: Claude đối chiếu với prototype rồi bạn duyệt (S5-7).
- **Mở sớm một phần Sprint 6** trong lúc chờ B3 (cổng vào Sprint 6 chưa đạt): hạng mục 3, 4 (phần Admin tạm dừng / đình chỉ; nhánh mất `charges_enabled` chờ hạng mục 1), 6, 7 (trừ tiến độ hoàn tiền, chờ hạng mục 5), FE 11, 12 với dữ liệu MSW. Hạng mục 1, 2, 5 chờ spike Stripe Connect (S5-4); hạng mục 8 chờ thăm dò Free plan (hạng mục 16 ở trên).

## Tình trạng đợt code Sprint 6 (nhánh `feature/sprint-6`, 22/10/2026)

Làm hạng mục 7, 10, 11, 12 (không bị chặn) theo yêu cầu người dùng: **bỏ qua viết test mới, bỏ qua `./mvnw verify` / `spotless:check` / Vitest / Playwright** để đi nhanh; chỉ `./mvnw compile` (BE) và `tsc -b` + `vite build` (FE) để chắc không lỗi cú pháp và kiểu. Riêng `OpenApiContractTests` được chạy thật (không phải test nghiệp vụ, mà là bước sinh `frontend/openapi/openapi.json` cho Orval) vì không có cách nào khác lấy contract mới. Đã commit từng hạng mục, chưa push.

| Hạng mục | Kết quả | Còn lại |
|---|---|---|
| 7 Admin dashboard/vận hành | `GET /admin/dashboard`, `GET /admin/events` (+ CSV), `GET /admin/bookings` mở rộng (+ CSV); cờ `needs_reconcile` trên `bookings` do Payment phát hiện khi đối soát quá hạn | – |
| 10 FE kết nối Stripe (#26) | 5 trạng thái, thanh 4 bước, modal mở Stripe, Express Dashboard, nút ngắt kết nối luôn khoá; link "Kết nối Stripe" trong hộp "Chưa thể publish" (#24) | – |
| 11 FE organizer số liệu | Tổng quan (#11) dùng `GET /organizer/dashboard`; bảng điều khiển sự kiện (#25, rút gọn — xem quyết định dưới) dùng `GET /organizer/events/{id}/sales`; đơn hàng (#14) có lọc + CSV server | Số đếm "đã vào/tổng vé" ở app check-in (ẩn theo `docs/design/checkin.md`) chưa bật lại — không nằm trong hạng mục 11, chỉ là API đã có |
| 12 FE Admin vận hành | Tổng quan (#35), sự kiện (#34: lọc, tạm dừng/mở lại/huỷ gõ "HUY", tạm dừng hàng loạt, tiến độ hoàn), đơn hàng & đối soát (#29: lọc rộng hơn + CSV, hoàn thủ công), phí (#30: lịch sử + đặt mức mới) | – |

### Quyết định / thoả hiệp khi làm (chưa hỏi bạn trước, cần duyệt lại)

- **Sửa lỗi OpenAPI có sẵn từ hạng mục 6**: hai `@GetMapping` cùng path phân biệt bằng `produces` (list JSON / export CSV, đúng quy ước `overview.md` §11.1) khiến springdoc chỉ giữ lại **một** operation cho path đó — có lúc mất hẳn response 200 của "list" (`OrganizerSalesController.bookings`, phát hiện khi Orval sinh ra thiếu type). Sửa bằng `@Hidden` trên method export ở cả 3 controller cùng kiểu (`OrganizerSalesController`, `AdminBookingController`, `AdminEventController`): export vẫn chạy đúng ở runtime (Spring tự phân biệt theo `Accept`), chỉ không vào tài liệu OpenAPI / Orval. FE gọi CSV bằng `fetch` thẳng tới URL của "list" kèm header `Accept: text/csv` (hàm `downloadServerCsv` mới ở `frontend/src/lib/csv.ts`), không qua Orval.
- **Cờ `needs_reconcile`** (đơn cần đối soát, cho `todo.reconcile` ở dashboard và lọc ở #29): thêm cột `bookings.needs_reconcile` (migration `V202610251500`). Payment phát `PaymentReconcileFlagged` (định nghĩa ở `booking.event`, theo đúng hướng phụ thuộc Payment → Booking đã có) khi job đối soát thấy payment quá hạn mà vẫn chưa chốt, và khi thanh toán chốt xong (`PaymentSettlementServiceImpl.markSucceeded` / `markCanceled`) thì phát lại với `flagged=false`. Hạn chế đã biết: nếu một payment được đánh cờ rồi tự chốt bằng đường ngoài `PaymentSettlementService` (hiện không có đường nào khác, nhưng ghi lại để không quên), cờ sẽ không tự tắt.
- **Bảng điều khiển sự kiện (#25)** rút gọn so với prototype `OrgEventSales.dc.html`: bỏ "vé bán theo giờ" (cần dữ liệu theo giờ, Sprint 7 mới có), bỏ số đếm phòng chờ (Sprint 7), bỏ "đơn hàng mới nhất tự cập nhật" (cần SSE, Sprint 7). Thay bằng biểu đồ doanh thu theo ngày (Recharts) + theo hạng vé + banner tạm dừng, đúng dữ liệu `GET /organizer/events/{id}/sales` đang có. Đặt thành tab thứ 4 ("Doanh thu") trong khung sửa sự kiện (`EventEditLayout`) thay vì màn độc lập như prototype, vì khung đó đã bọc mọi route con bằng header + thanh tab — tách riêng cần đổi cấu trúc route nhiều hơn mức cần cho sprint này.
- **"Tuỳ chọn khoảng ngày"** làm thành component riêng `DateRangeFilter` (không nhúng vào `FacetFilter` single-mode) để không ảnh hưởng các chỗ đang dùng `FacetFilter` (sự kiện, hồ sơ organizer…). Dùng cho đơn hàng organizer (#14) và sự kiện Admin (#34).
- Thêm `recharts@3.2.1` vào `frontend/package.json` (chưa dùng trước đó). Đã kiểm `npm audit`: không thêm lỗ hổng mới (toàn bộ cảnh báo hiện có đến từ `orval` / `shadcn`, là dev dependency có từ trước).
- Thêm `CatalogApi.organizerNames`, `countPendingOrganizers`, `countSuspendedEvents`, `countEventsOnSale`, `openingSoonEvents` và `BookingApi.adminTotals`, `countNeedsReconcile`, `countRefundFailed`: đọc được vì Payment đã phụ thuộc cả Booking và Catalog (ADR 0018), không tạo vòng mới.

## Đợt MR Sprint 6 (nhánh `feature/sprint-6`, 22/10/2026)

Hạng mục 1–6 đã commit từ trước (`9072c33`, `27fb3c1`, `776b15b`, `ce795d6`, `15a69d5`) và có test riêng. Phần MR làm tiếp, mỗi hạng mục một commit, chưa push; chưa viết test mới cho 14, 15, 20, 21 (nằm trong S6-1).

| Hạng mục | Kết quả | Còn lại |
|---|---|---|
| 14 Audit log | `GET /admin/audit-logs` (lọc người thao tác, hành động, thời gian), màn #31 | – (xuất CSV tối đa 10.000 dòng, `422 EXPORT_TOO_LARGE`, ghi audit; nút xuất ở màn #31) |
| 15 Địa điểm, đơn vị hành chính | CRUD `/admin/venues` (+ `bulk-delete`), `/admin/administrative-units`; màn #16, #33 | – (`GET /administrative-units` có cache trong bộ nhớ, TTL `tickethub.catalog.administrative-unit-cache-ttl` mặc định 10 phút, chỉ cache truy vấn không có từ khoá, Admin đổi thì xoá cache của pod đó, pod khác chờ hết TTL) |
| 20 Cấu hình thanh toán | `GET /admin/payment-config`, `stripe-events`, `reprocess`, `webhook-check`; màn #36 | – |
| 21 Organizer | `duplicate`, `bulk-duplicate`, `GET/PUT /organizer/profile`, `bulk-resend-tickets`; màn #27 (hồ sơ, thông báo) | – (nút nhân bản ở #12, gửi lại vé hàng loạt ở #14) |
| 16 Spike `generateSeats` | Xong: `generateSeats.ts` và `SeatGenerator.java` ra cùng toạ độ trên bộ dữ liệu chung `shared-test-data/generate-seats.json` (9 ca, 82 ghế; kỳ vọng tính độc lập bằng Python); không cần lùi về "form + lưới" | – |
| 17 Sơ đồ ghế BE | `GET/POST/PUT/DELETE /admin/seat-maps`, `POST /{id}/copy`; lưu thì sinh lại ghế, khu giữ `id` theo `code`; `SEAT_MAP_LOCKED`, `SEAT_MAP_BEING_EDITED`, `SECTION_IN_USE`; 5 test tích hợp | Quyết định chờ duyệt bên dưới |
| 19 Import Excel | Xong: `GET /admin/imports/templates/{type}`, `POST /admin/imports/{type}?dryRun=&onDuplicate=&seatMapId=`, `GET /admin/imports/{id}/errors`; `import_jobs`, file gốc và file lỗi lưu ở object storage; Apache POI 5.4.1; `venues.code` (cột mới, khớp khi import lại); modal 3 bước ở #16, #33; 5 test BE, 3 test FE | Modal import ở #17 gắn khi làm editor (hạng mục 18); chỉ nhận `.xlsx` (chưa nhận `.csv`); chưa kiểm trên S3 thật |
| 18 FE editor sơ đồ ghế | Chưa làm | Làm sau 19; gồm modal import ở #17 |

### Quyết định khi làm sơ đồ ghế (chưa hỏi trước, cần duyệt)

- **Khoá `lock:seatmap:{id}` dùng Lettuce (`SET NX PX` + compare-and-delete), không dùng Redisson** như §9: repo chưa có Redisson và không cài được ở đây. Khoá TTL 60 giây, không tự gia hạn vì một lần lưu có giới hạn kích thước (tối đa 50.000 ghế). Nếu muốn đúng Redisson thì thêm dependency rồi thay `SeatMapLock`, giao diện không đổi.
- **`seats.x, y` là INTEGER nên toạ độ sinh ra được làm tròn** khi lưu; editor vẽ bằng `generateSeats` (2 chữ số thập phân), lệch tối đa 0,5 đơn vị so với toạ độ đã lưu.
- Khu `IMPORTED` chỉ đổi được tên và `shape` khi lưu, ghế giữ nguyên; tạo khu `IMPORTED` chỉ qua import Excel (hạng mục 19).
- Độ cong của hàng là parabola (`curve` = độ nhô ở giữa hàng, đơn vị toạ độ), không phải cung tròn thật.

## Việc treo của Sprint 6

| # | Việc | Bị chặn bởi | Làm gì khi được gỡ |
|---|---|---|---|
| S6-1 | Nợ kiểm chứng (bỏ qua theo yêu cầu đi nhanh): chưa viết test cho hạng mục 7, 10, 11, 12, 14, 15, 20, 21 (hạng mục 1–6 đã có test từ trước); chưa chạy `./mvnw verify`, `spotless:check`, Vitest, Playwright toàn bộ | Không bị chặn, chỉ hoãn | Viết test theo `ticket-hub-testcase-rule` cho: BE (`AdminDashboardServiceImpl`, `AdminEventListServiceImpl`, cờ `needs_reconcile` 2 chiều, CSV export mới); FE (Vitest cho các trang mới, Playwright mock cho luồng tạm dừng / huỷ / hoàn thủ công / kết nối Stripe) |
| S6-2 | AWS lab 2 + gỡ AWS (hạng mục 8, 9 Sprint 6) | B6 (thăm dò Free plan + xác nhận credits), duyệt `terraform apply` từng lần | Làm theo hạng mục 8, 9 của plan Sprint 6 khi B6 xong |
| S6-3 | Verify lõi đầy đủ (hạng mục 13) và review độc lập dòng tiền Connect / hoàn hàng loạt (hạng mục 23) | Hạng mục 1–12 (còn 8, 9 AWS); S6-1 | Chạy lại mọi test, đối chiếu AC-1 → AC-14 của plan Sprint 6, ghi `docs/test/sprint-6-verify.md`; gọi subagent review độc lập theo skill `code-review` cho `payment` và `booking` |
| S6-4 | Hạng mục MR 18 (FE editor sơ đồ ghế) chưa làm; 14, 15, 16, 17, 19, 20, 21 đã có code (xem "Đợt MR") | Không bị chặn | Làm 18, gồm modal import ở #17 |
| S6-5 | ~~Side sheet chi tiết đơn có dòng thời gian Stripe ở #29~~ **Xong 22/10/2026**: `GET /admin/bookings/{id}/payment` (khoản thu, lần hoàn, timeline từ `stripe_events` khớp theo PaymentIntent), `OrderPaymentSheet`; 2 test BE, 1 test FE | – | – |
| S6-6 | ~~Thông báo "Stripe bị hạn chế" cho Admin~~ **Xong 22/10/2026**: Payment phát `StripeAccountRestricted` khi chuyển sang `restricted` (không phát lại khi đã restricted), Notification báo Admin; có test | – | – |
