# Plan: Sprint dọn dẹp (giữa Sprint 4 và Sprint 5)

**Mục tiêu:** xử lý hết việc treo của Sprint 1–4 trước khi mở Sprint 5: code lên `main` qua PR với CI xanh, trả các nợ kỹ thuật đã chốt, chạy thật những gì mới kiểm bằng mock (Stripe, k3d, observability). Xong sprint thì [`2026-10-21-pending-sprint-1-4.md`](2026-10-21-pending-sprint-1-4.md) chỉ còn những dòng gắn với tính năng của Sprint 5–8.

**Đầu vào:** file pending ở trên (mã dòng `S<n>-U/V/T<n>`); quyết định của người dùng ngày 21/10/2026 (mục "Quyết định đã chốt"). Mỗi hạng mục chỉ đọc đúng các § `overview.md` và ADR ở cột "Đọc".

**Ngoài sprint này:** việc phụ thuộc tính năng chưa có, giữ ở sprint sau theo cột "Vào plan" của file pending (tab phòng chờ, nút "Kết nối Stripe", editor sơ đồ ghế, PgBouncer, `SALE_PAUSED`, BR-08, DLT…); chia lại package `shared` (`S3-T4`, giữ nguyên).

## Hạng mục

| # | Việc | Vùng | Đọc | Phụ thuộc | Xong khi |
|---|---|---|---|---|---|
| 1 | Đưa Sprint 1–4 lên `main`: push 4 nhánh, mở PR theo thứ tự, CI chạy lần đầu, sửa đỏ, squash merge, rebase nhánh sau lên `main`; bật bảo vệ `main`, Renovate, SonarCloud, `NVD_API_KEY` (`S1-U1`, `U2`, `U3`, `U6`, `S2-U4`, `S3-U2`) | Infra | `docs/ops/ci-cd.md`; CLAUDE.md (Git) | — | 4 PR đã merge, CI xanh trên `main`, image 3 ứng dụng có trên GHCR |
| 2 | Job cập nhật image tag mở PR tự động thay vì push thẳng `main`, CI xanh thì tự merge (`S3-T8`, `S3-T9`) | Infra | §19.3; skill infra | 1 | Merge một thay đổi vào `main` → bot mở PR đổi tag → tự merge → ArgoCD thấy tag mới |
| 3 | Nợ nhỏ: `.gitattributes` (`* text=auto eol=lf`) và chuẩn hoá xuống dòng để `spotless:check` xanh trên Windows (`S2-T10`); `.env.example` đủ `STRIPE_*`, `TICKET_SIGNING_JWK`, `MAIL_*` và truyền vào `app`, thêm `frontend/.env.example`, service stripe-mock trong compose dev (`S3-T1`, `S3-T2`); khai `Idempotency-Key` trong OpenAPI của `POST /bookings` rồi sinh lại client, FE bỏ truyền header tay (`S2-T1`); `PENDING_BOOKING_EXISTS` kèm `bookingId` + FE link về đơn đang giữ (`S2-T2`) | BE, FE, Infra | §6.7, §11.1, §19.2 | 1 | `spotless:check` xanh trên máy dev; `docker compose --profile core up` chạy được với `.env` sinh từ `.env.example`; hàm Orval `createBooking` có tham số `idempotencyKey` |
| 4 | `booking` chuyển từ JdbcTemplate sang entity + Spring Data JPA; SQL đặc thù (`FOR UPDATE` theo thứ tự `seat_id`, `UPDATE … WHERE status = 'AVAILABLE'`, `RETURNING`, upsert `ON CONFLICT … WHERE`) giữ bằng native query; cột `version` cho optimistic lock theo convention (`S3-T3`) | BE | §6.2, §10; ADR 0020, 0021; skill springbot (JPA) | 1 | Test 50 luồng tranh 1 ghế, test deadlock, truy vấn ghế trùng §10 và toàn bộ test `booking` xanh, số test không giảm |
| 5 | Thanh toán đến muộn và mở lại checkout (`S3-T5`, `S3-T6`): Booking ghi lại khi nhận `PaymentSucceeded` cho đơn `EXPIRED` / `CANCELLED` mà không giữ lại được ghế, cập nhật khi nhận `RefundCompleted` / `RefundFailed`; `GET /bookings/{id}` thêm `latePayment` (`REFUND_PENDING` / `REFUNDED` / `REFUND_FAILED`); `POST /payments` khi payment của đơn đã `SUCCEEDED` → `409 PAYMENT_ALREADY_SUCCEEDED`. FE checkout: hiện "Không giữ lại được ghế, tiền sẽ được hoàn" theo `latePayment`; nhận `PAYMENT_ALREADY_SUCCEEDED` → "Đang xác nhận thanh toán" và hỏi lại trạng thái booking. Ghi vào §7.3, §11.2 | BE, FE | §7.2, §7.3, §7.7, §11.2; ADR 0019 | 4 | Integration test cả hai nhánh; Playwright (mock): đơn hết hạn có `latePayment = REFUND_PENDING` hiện đúng thông báo; tải lại trang sau khi đã trả không hiện lại form |
| 6 | Hồ sơ cá nhân đầy đủ (#19, `S1-T1`): `user_profiles` thêm `avatar_url`, `phone`, `date_of_birth`, `province_id` (→ `administrative_units` cấp 1); `GET/PUT /me/profile`, `POST /me/avatar` (dùng lại cách upload banner); phiên đăng nhập `GET /me/sessions`, `DELETE /me/sessions/{id}` và xoá tài khoản `DELETE /me` qua Keycloak Admin API của module `account`. Email, tên, mật khẩu vẫn do Keycloak giữ. Sửa §6.6, §11.2 | BE, FE | §6.6, §11.2, §13.2; ADR 0016; prototype #19 | 1 | Integration test (Testcontainers Keycloak): sửa hồ sơ, đăng xuất 1 phiên, xoá tài khoản; user khác không đọc / sửa được; Playwright #19 qua ở 1280px và 390px |
| 7 | Trường sự kiện còn thiếu (#24, `S2-T5`): `events` thêm `doors_open_at`, `age_restriction` (số tuổi tối thiểu, có thể trống), `rules` (văn bản); sửa được cả sau publish, bổ sung vào danh sách thông tin hiển thị của BR-14; hiện ở chi tiết sự kiện (#3) và vé (#9). Bỏ khỏi prototype: thẻ, chế độ chỉ-link, kết thúc bán, xem trước | BE, FE | §6.1, §11.2; BR-14; prototype #3, #9, #24 | 1 | Test: sửa 3 trường sau publish → 200, `EventUpdated` cập nhật `event_snapshots`; `doors_open_at` sau `starts_at` → 422 |
| 8 | Thông báo còn thiếu: organizer nhận "khách yêu cầu hoàn" (`S4-T6`); gửi email theo cờ `email` của loại tuỳ chọn trong `notification_preferences` (`S4-T7`); job ShedLock "sắp hết giờ giữ ghế" (`HOLD_EXPIRING`): booking `PENDING_PAYMENT` chưa bắt đầu thanh toán còn ≤ 2 phút → 1 thông báo (`S4-T5` phần `HOLD_EXPIRING`) | BE | §6.5; BR-03 | 1 | Integration test: mỗi loại tạo đúng 1 thông báo, gửi lại event không trùng; tắt cờ `email` → không gửi email, vẫn có thông báo trong app |
| 9 | Check-in theo giờ sự kiện (`S4-T9`): chỉ nhận từ 3 giờ trước `starts_at` tới `ends_at`, ngoài khoảng → `422 CHECKIN_NOT_OPEN` / `EVENT_ENDED`; phát `TicketCheckedIn`. Ghi quy tắc vào BR-09 và §7.5; FE check-in hiện đúng thông báo | BE, FE | §6.4, §7.5, §8.1; BR-09; ADR 0024 | 1 | Test biên: đúng 3 giờ trước → nhận, sớm hơn 1 giây → `CHECKIN_NOT_OPEN`; sau `ends_at` → `EVENT_ENDED` |
| 10 | FE nhỏ: chạm vào khu trên sơ đồ ghế ở màn hẹp thì zoom vào khu đó rồi mới chọn ghế (`S2-T12`); "Thêm vào lịch" ở vé (#9) bằng file `.ics` sinh ở FE, bỏ "Tải PDF" và hoá đơn khỏi prototype, giữ lọc vé / đơn ở client (`S3-T7`); breadcrumb 4 cấp ở trang sửa sự kiện (`S2-T4`); layout khách thêm ô tìm kiếm, link "Bán vé cùng Ticket Hub", tab danh mục (`S1-T8`) | FE | §14.4; prototype #1, #5, #9, #13, #24 | 1 | Playwright 390px: chạm khu → zoom → chọn được ghế; file `.ics` có giờ theo `Asia/Ho_Chi_Minh`; component test breadcrumb |
| 11 | E2E trên stack thật (compose `core`, không cần Stripe) cho giữ ghế và publish (`S2-T9`) | FE, Verify | §20.1; skill testcase-rule | 3 | Playwright chạy với `app` + `gateway` thật trong CI |
| 12 | Chạy thật với Stripe test (`S3-U1`, `U3`, `V1`–`V5`, `S4-U4`, `U5`, `V1`, `V2`): `StripeGateway` trên stripe-mock, checkout trên trình duyệt thật (thẻ 4242, 390px), `purchase.spec.ts` với `E2E_STRIPE=1`, hoàn tiền thật (thẻ `4000 0000 0000 5126` → `refund.failed`, số tiền 950.000) | Verify | §7.2, §7.4; ADR 0022 | 3, B2 | Ghi kết quả vào `docs/test/sprint-cleanup-verify.md`; payload thật khác giả định thì sửa code và test |
| 13 | Dựng k3d trên máy staging và chạy phần hạ tầng đã viết (`S3-U4`, `U5`, `V6`, `T10`, `S4-U1`, `V4`, `V5`, `T1`, `T2`): Helm, ArgoCD app-of-apps, CoreDNS rewrite `auth.tickethub.localhost`; Garage (ADR 0033) làm object storage; CNPG + WAL archive + base backup; diễn tập PITR (C7) và kill primary (C1); bản sao backup ra ngoài máy staging (`S4-T4`, chờ quyết định A2) | Infra | §18, §19.4, §20.3 (C1, C7); ADR 0033; `docs/ops/k8s-staging.md`, `docs/ops/gitops.md` | 1, 2, B3 | App chạy trên k3d qua ArgoCD; PITR ≤ 15 phút, mất ≤ 5 phút; failover < 30 giây |
| 14 | Observability trên cụm (`S4-U2`, `U3`, `V6`–`V9`, `T11`): `helm template` / `promtool check rules`, đối chiếu tên metric với `/actuator/prometheus` thật, alert giả lập tới Telegram, cert 1 giờ tự gia hạn (C9), trace Gateway → app trong Jaeger, lỗi JS trong Sentry, NetworkPolicy cho `/actuator/prometheus` | Infra | §16; `docs/ops/observability.md` | 13, B5, B6 | AC-10 → AC-13 của Sprint 4 có bằng chứng chạy thật |
| 15 | Check-in trên điện thoại thật qua HTTPS: camera, âm thanh, rung, Safari iOS (`S4-V3`) | Verify | §7.5 | 9, 13, B9 | Quét được vé thật; ghi kết quả vào báo cáo verify |
| 16 | AWS: checklist bảo vệ tài khoản, Terraform thăm dò Free plan, lab 1 rồi destroy + `verify-clean.sh` (`S1-U4`, `U5`, `S2-U3`) | Infra | §23; `infra/terraform/aws/` | 1, B7 | `docs/ops/aws-free-plan-probe.md` đủ bảng; lab 1 chạy được và sạch sau destroy |
| 17 | Duyệt tài liệu (người dùng): `docs/design/seat-map.md` (`S2-U1`), `docs/design/checkout.md` (`S3-U6`), quyết định nhỏ Sprint 4 (`S4-U6`); Claude sửa theo góp ý | Docs | — | — | Người dùng đã duyệt cả 3 |
| 18 | Review còn thiếu: review cuối Sprint 2 kèm review độc lập SQL giữ ghế (`S2-U2`), review độc lập phần thanh toán Sprint 3 (`S3-U7`); chạy sau hạng mục 4 để review luôn bản JPA | Review | skill `code-review`, `tech-lead` | 4, 5 | Hai báo cáo review; phát hiện Critical / High đã sửa |
| 19 | Verify toàn sprint, dọn file pending (xoá dòng đã xong), review cuối sprint | Verify, Review | AC bên dưới | 1–18 | `docs/test/sprint-cleanup-verify.md`; file pending chỉ còn dòng gắn Sprint 5–8; APPROVED |

## Thứ tự

1. 1 trước tiên (mọi hạng mục khác merge qua PR); 2, 3 ngay sau.
2. Code: 4 → 5; 6, 7, 8, 9, 10 song song sau 1; 11 sau 3. Hạng mục 4 xong trước Sprint 5 vì Sprint 5 sửa đúng lớp giữ ghế.
3. Việc chờ người dùng (12–17) làm ngay khi việc chặn tương ứng (B2–B9 trong file pending) được gỡ, không chờ phần code.
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
- AC-11: App chạy trên k3d qua ArgoCD; PITR và failover đạt chỉ tiêu Sprint 4 (AC-10, AC-11 của Sprint 4); backup có bản sao ngoài máy staging (nếu A2 chọn làm).
- AC-12: Alert giả lập tới Telegram, trace Gateway → app trong Jaeger, lỗi JS trong Sentry (AC-12, AC-13 của Sprint 4).
- AC-13: File pending không còn dòng nào ngoài các dòng gắn với hạng mục của Sprint 5–8.

## Thao tác cần báo trước

Push, mở PR, merge (hạng mục 1, 2 và mọi PR sau đó); `kubectl` / `helm` lên k3d; diễn tập PITR và kill primary trên staging; **`terraform apply` lab 1 (hỏi từng lần)**, `terraform destroy`; đặt Stripe key, Telegram token, Sentry DSN, `NVD_API_KEY` vào Secret / `.env` (không commit).

## Quyết định đã chốt (người dùng, 21/10/2026)

- Object storage cho backup: Garage (ADR 0033).
- Thanh toán đến muộn / mở lại checkout: dùng dữ liệu sẵn có của Booking và Payment, không thêm API trạng thái thanh toán riêng.
- Hồ sơ cá nhân: lưu thêm trường trong `user_profiles`; phiên và xoá tài khoản qua Keycloak Admin API.
- Trường sự kiện: chỉ thêm giờ mở cửa, độ tuổi, quy định.
- Thêm thông báo "khách yêu cầu hoàn" cho organizer và email theo cài đặt; check-in mở từ 3 giờ trước giờ diễn tới giờ kết thúc.
- Cập nhật image tag bằng PR tự động; không chạy `app` / `gateway` dạng container trong compose dev.
- Zoom theo khu ở màn hẹp; "Thêm vào lịch" bằng `.ics`; bỏ "Tải PDF" và hoá đơn.
- `booking` chuyển sang JPA; package `shared` giữ nguyên.

## Câu hỏi mở

- [BLOCKING phần cuối việc 13] A2: có làm bản sao backup ra ngoài máy staging không, và chép đi đâu.
- Giả định ở việc 6: "tài khoản liên kết" (đăng nhập bằng Google / Facebook) chỉ có nghĩa khi realm có identity provider; realm hiện chưa có nên bỏ mục này khỏi #19. Xoá tài khoản: xoá user ở Keycloak, ẩn danh hoá `user_profiles`, giữ booking và vé đã qua để đối soát.
