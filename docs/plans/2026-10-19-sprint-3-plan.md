# Plan: Sprint 3. Bán vé phần 2: thanh toán, vé, lên Kubernetes

**Mục tiêu:** bán trọn một vé từ đầu đến cuối (chọn ghế → trả bằng thẻ test → vé QR + email), có đối soát khi mất webhook; app chạy trên k3d theo GitOps.

**Đầu vào:** mỗi hạng mục chỉ đọc đúng các § `overview.md` và ADR ở cột "Đọc"; plan Sprint 2 (code đã có).

**Ngoài sprint này:** hoàn vé theo yêu cầu khách (BR-07), `REFUND_FAILED`, hoàn thủ công, phát vé vi phạm UNIQUE → hoàn tự động, check-in, Resilience4j (Sprint 4); Stripe Connect, `charges_enabled` (Sprint 6); CNPG, giám sát (Sprint 4).

## Hạng mục

| # | Việc | Vùng | Đọc | Phụ thuộc | Xong khi |
|---|---|---|---|---|---|
| 1 | Schema `payment`: `payments`, `refunds`, `stripe_events`, `processed_events`. Adapter `StripeGateway` (stripe-java, timeout 3 giây, key từ biến môi trường); bộ test với stripe-mock / WireMock | BE | §6.3, §15.1 (timeout); ADR 0022; skill springbot mục 9 | S2 | Test adapter tạo / lấy / huỷ PaymentIntent trên stripe-mock |
| 2 | Bắt đầu thanh toán: `BookingApi.startPayment` (chủ booking, `PENDING_PAYMENT`, còn ≥ 90 giây, ghi `payment_started_at`, trả số tiền và `platform_fee_bps`); `POST /payments` (bắt buộc `Idempotency-Key`) tạo PaymentIntent với idempotency key `booking-{id}`, lưu `application_fee_minor`, trả `clientSecret` | BE | §7.2 (bước POST /payments), §6.3; BR-03, 06, 10; ADR 0018, 0022, 0025 | 1 | Còn < 90 giây → `409 HOLD_EXPIRING`; gửi lại cùng key → cùng `clientSecret`, PI chỉ tạo 1 lần; test làm tròn phí |
| 3 | Webhook `POST /webhooks/stripe`: verify chữ ký trên raw body (lệch > 5 phút → từ chối), dedupe `stripe_events`, máy trạng thái payment (`processing`, `succeeded`, `payment_failed`), phát `PaymentSucceeded` / `PaymentFailed`; test đi qua Gateway (miễn CSRF) | BE | §7.7 (Payment), §13.1 (dòng CSRF), §13.4 (Webhook giả mạo); ADR 0019 | 2 | Sai chữ ký → 400; webhook trùng xử lý 1 lần; sự kiện sai thứ tự bị bỏ qua có log |
| 4 | Xác nhận booking: nghe `PaymentSucceeded` → ghế `HELD → SOLD`, booking `CONFIRMED`, phát `BookingConfirmed` (kèm tên sự kiện, giờ diễn, địa điểm, ghế, `contact_email`). Payment nghe `BookingExpired` / `BookingCancelled` → huỷ PI; huỷ thất bại vì PI đã `succeeded` → lấy PI và xử lý như `PaymentSucceeded` | BE | §7.2, §7.3 (dòng 1, 3), §7.7 (Booking); ADR 0019 | 3 | Integration test luồng đầy đủ trên stripe-mock; booking hết hạn → PI bị huỷ |
| 5 | Thanh toán đến muộn: booking `EXPIRED` / `CANCELLED` nhận `PaymentSucceeded` → giữ lại đúng các ghế (sự kiện còn `ON_SALE`) → `CONFIRMED`; không giữ được → `BookingConfirmationFailed` → Payment hoàn 100% (bản ghi `refunds`, key `refund-{refundId}`) | BE | §7.3 (dòng 5), §7.4 (phần Payment), §7.7; ADR 0019, 0026 | 4 | Test hai nhánh: ghế còn trống → `CONFIRMED`; ghế đã bán → hoàn 100%, booking giữ nguyên trạng thái |
| 6 | Đối soát: job (mỗi 5 phút, ShedLock) quét payment `REQUIRES_PAYMENT` / `PROCESSING` quá 15 phút, hỏi Stripe, xử lý như webhook tương ứng. Metric "payment `SUCCEEDED` mà booking chưa `CONFIRMED` / chưa hoàn sau 5 phút" (alert ở Sprint 4) | BE | §7.3 (dòng 6), §16.3; ADR 0019 | 4 | Test mất webhook: PI `succeeded` mà không có webhook → đối soát đưa booking về `CONFIRMED` |
| 7 | Ticket: schema `tickets` (unique index vé chưa thu hồi), `signing_keys`; khoá ES256 từ biến môi trường / K8s Secret theo `kid`; nghe `BookingConfirmed` → mỗi ghế một vé, QR là JWT `{ticketId, eventId, seat, kid}`; phát `TicketIssued` một lần cho cả booking (kèm thông tin để gửi email); `GET /me/tickets` | BE | §6.4, §7.5 (bước 1), §13.4 (Vé giả); ADR 0024 | 4 | Nhận `BookingConfirmed` 2 lần → không phát vé trùng; JWT verify được bằng public key; chỉ thấy vé của mình |
| 8 | Notification: `templates`, `notification_log`; nghe `TicketIssued` → email vé (Thymeleaf, Mailpit) gửi tới `contact_email`, idempotent. Booking: `GET /me/bookings` | BE | §6.5, §6.2 (`contact_email`), §11.2 | 7 | Email có trong Mailpit; nhận event 2 lần → 1 email |
| 9 | Đối chiếu prototype màn #6–#9, #21; ghi `docs/design/checkout.md` chỉ phần prototype chưa thể hiện: lỗi Stripe Payment Element, chờ `CONFIRMED`, hết giờ giữ ghế giữa lúc trả | FE | §14.4 (Checkout); BR-03, 07, 12 | — | Người dùng duyệt phần bổ sung |
| 10 | FE checkout (#6) và thành công (#7): Stripe Payment Element, dòng phí nền tảng "không hoàn lại", đồng hồ theo `expiresAt`, khoá nút khi còn < 90 giây, xử lý `HOLD_EXPIRING`, thẻ bị từ chối cho thử lại; sau khi trả tiền thì hỏi lại trạng thái booking tới khi `CONFIRMED` rồi chuyển sang vé | FE | §14.4; BR-03, 07; skill react mục 3, 9 | 2, 9 | Component test các trạng thái; thẻ test 4242 thành công |
| 11 | FE "Vé của tôi" (#8, #9; QR bằng qrcode.react) và "Đơn của tôi" (#21) | FE | §14.2 (Customer) | 7, 8, 9 | Hiển thị đúng vé / đơn của user đang đăng nhập |
| 12 | Helm: library chart + chart `app`, `gateway`, frontend (probe, resources, non-root, PDB); Postgres / Redis / Keycloak bằng chart đơn giản; tạo cụm k3d trên máy staging | Infra | §19.1, §19.4; skill infra mục 7 | S1 | `helm lint` / `helm template` sạch; app chạy trên k3d, đăng nhập được |
| 13 | ArgoCD (app-of-apps); CI cập nhật image tag trong values → ArgoCD đồng bộ; Gateway API + Envoy Gateway, cert-manager (CA tự ký cho local), NetworkPolicy; Trivy và OWASP Dependency-Check trong CI | Infra | §19.3, §19.4, §13.4 (Mạng, Dependency); ADR 0014 | 12 | Commit đổi image tag → k3d tự cập nhật; truy cập qua HTTPS của Envoy; pod khác không gọi thẳng được `app` |
| 14 | E2E Playwright mua trọn vé trên compose: thẻ test Stripe, webhook chuyển về local bằng Stripe CLI; chạy truy vấn ghế trùng sau test | FE, Verify | §20.1 (E2E), §10 (truy vấn ghế trùng) | 4–11 | Playwright xanh; truy vấn ghế trùng = 0 |
| 15 | Verify toàn sprint theo AC bên dưới, kèm bằng chứng | Verify | AC bên dưới | 1–14 | Báo cáo verify |
| 16 | Review cuối sprint (skill `tech-lead`; review độc lập bằng subagent cho phần thanh toán) | Review | — | 15 | APPROVED / CHANGES REQUESTED |

## Thứ tự

1. BE: 1 → 2 → 3 → 4 → (5 ‖ 6 ‖ 7) → 8.
2. FE: 9 ngay; 10 sau 2 (mock trước bằng MSW); 11 sau 7, 8.
3. Infra: 12 → 13, song song với BE / FE.
4. 14 → 15 → 16.

## API cho FE mock

| Endpoint | Request chính | Response chính | Mã lỗi nghiệp vụ |
|---|---|---|---|
| `POST /payments` | `{bookingId}` + `Idempotency-Key` | `{paymentId, clientSecret, amountMinor, platformFeeMinor, currency}` | `HOLD_EXPIRING`, `BOOKING_NOT_PAYABLE` |
| `GET /bookings/{id}` | — | thêm `status = CONFIRMED` sau khi trả tiền | — |
| `GET /me/tickets` | `page`, `size` | `[{ticketId, eventTitle, startsAt, venueName, seat, status, qrToken}]` | — |
| `GET /me/bookings` | `page`, `size` | `[{bookingId, eventTitle, startsAt, status, totalMinor, currency, createdAt}]` | — |

## Acceptance criteria

- AC-1 (BR-03): Booking còn < 90 giây → `POST /payments` trả `409 HOLD_EXPIRING`; còn ≥ 90 giây → trả `clientSecret`, ghi `payment_started_at`, và booking không bị hết hạn trong 5 phút ân hạn.
- AC-2: Trả bằng thẻ test `4242…` → booking `CONFIRMED`, mọi ghế `SOLD`, mỗi ghế một vé `VALID`, email vé có trong Mailpit trong vòng 2 phút.
- AC-3: Thẻ bị từ chối (`4000 0000 0000 0002`) → báo lỗi rõ ràng; thử thẻ khác trong hạn giữ ghế → thành công, vẫn một payment.
- AC-4: Webhook sai chữ ký hoặc lệch > 5 phút → 400, không đổi trạng thái; cùng `stripe_event_id` gửi 2 lần → xử lý 1 lần; webhook đi qua Gateway không bị chặn CSRF.
- AC-5 (mất webhook): PI đã `succeeded` mà không có webhook → job đối soát đưa booking về `CONFIRMED` và phát vé.
- AC-6 (đến muộn): Booking `EXPIRED` nhận `PaymentSucceeded`: ghế còn trống → `CONFIRMED`; ghế đã bán cho người khác → hoàn 100% tự động, booking giữ `EXPIRED`.
- AC-7 (BR-12 + thanh toán): Khách huỷ booking đã tạo PI → PI bị huỷ.
- AC-8 (BR-10, BR-06): `amount_minor` VND không nhân 100; `application_fee_minor = floor(total × bps / 10000)` (1.000.000 × 500 → 50.000; 333.333 × 500 → 16.666).
- AC-9: QR là JWT ES256 verify được bằng public key theo `kid`; `GET /me/tickets` và `GET /me/bookings` chỉ trả dữ liệu của người đang đăng nhập.
- AC-10: Playwright mua trọn vé chạy xanh; truy vấn ghế trùng (§10) trả 0 dòng.
- AC-11: Đổi image tag trong Git → ArgoCD cập nhật k3d; app chạy qua HTTPS của Envoy Gateway; Trivy chạy trong CI.

## Thao tác cần báo trước

Commit / push / PR / merge; `kubectl` / `helm` lên cụm k3d staging; cài ArgoCD; đặt Stripe test key vào `.env` / K8s Secret (không commit).

## Quyết định nhỏ trong sprint

- Giai đoạn chưa có Connect: **không gửi** `application_fee_amount` lên Stripe (chỉ dùng được khi có connected account), chỉ lưu `application_fee_minor` trong DB (ADR 0022).
- `refunds` có từ sprint này để phục vụ nhánh hoàn 100% khi đến muộn; hoàn theo yêu cầu khách làm ở Sprint 4.
- `TicketIssued` phát một lần cho cả booking, mang đủ thông tin để Notification gửi email, không cần bản sao riêng.
- FE hỏi lại trạng thái booking sau khi trả tiền (polling ngắn); SSE làm ở Sprint 7.

## Câu hỏi mở

- [BLOCKING trước việc 2] Bạn tạo tài khoản Stripe ở test mode (quốc gia Singapore, theo ghi chú tiền tệ ở `overview.md` §3), rồi đặt secret key và webhook secret vào `.env` local. Claude không tạo tài khoản thay bạn.
- Repo GitHub là **public**: ArgoCD đọc repo không cần credential. Vì repo công khai, mọi secret (Stripe key, webhook secret, client secret Keycloak) chỉ nằm trong `.env` local / K8s Secret; gitleaks chặn ở pre-commit và CI.
