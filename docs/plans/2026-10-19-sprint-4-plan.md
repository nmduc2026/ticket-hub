# Plan: Sprint 4. Vận hành cơ bản và đúng đắn khi có lỗi

**Mục tiêu:** đủ vòng đời của khách (mua, hoàn vé, vào cổng); mọi nhánh lỗi ở §7.3 và §7.4 có xử lý và có test; hệ thống quan sát được, khôi phục được.

**Đầu vào:** mỗi hạng mục chỉ đọc đúng các § `overview.md` và ADR ở cột "Đọc"; plan Sprint 3 (code đã có).

**Ngoài sprint này:** huỷ sự kiện và hoàn hàng loạt (BR-08), dòng tiền Connect khi hoàn (Sprint 6); ELK, Redis Sentinel, chaos C2 / C4 / C8 (Sprint 5); check-in offline (ngoài phạm vi dự án).

## Hạng mục

| # | Việc | Vùng | Đọc | Phụ thuộc | Xong khi |
|---|---|---|---|---|---|
| 1 | Yêu cầu hoàn vé: `POST /bookings/{id}/refund` (chủ booking, bắt buộc `Idempotency-Key`, booking `CONFIRMED`, còn ≥ 48 giờ trước giờ diễn theo `event_snapshots`) → `REFUND_REQUESTED`, phát `RefundRequested`. Ticket nghe → thu hồi mọi vé của booking → `TicketRevoked`. Booking nhả ghế về `AVAILABLE` khi đã nhận `TicketRevoked` của mọi vé | BE | §7.4, §7.7 (Booking); BR-07, 09; ADR 0026 | S3 | Test biên 48 giờ (đúng 48h, thiếu 1 giây); ghế chỉ trống lại sau khi mọi vé đã thu hồi |
| 2 | Phía Payment: nghe `RefundRequested` → tạo bản ghi `refunds` rồi gọi Stripe (key `refund-{refundId}`); số tiền = đã trả − `application_fee_minor` (lý do khách hoàn) hoặc 100% (lý do phát vé lỗi); webhook `charge.refunded` → `RefundCompleted`, `refund.failed` → `RefundFailed`; Booking → `REFUNDED` / `REFUND_FAILED`; email xác nhận hoàn | BE | §6.3, §7.4, §7.7; BR-07, 10; ADR 0022, 0026 | 1 | Test số tiền 1.000.000 → hoàn 950.000; thẻ test hoàn thất bại → `REFUND_FAILED` |
| 3 | Admin hoàn thủ công: `GET /admin/bookings?status=REFUND_FAILED`, `POST /admin/bookings/{id}/refund` (`REFUND_FAILED → REFUND_REQUESTED`, tạo lần thử mới). Thêm endpoint danh sách vào `overview.md` §11.2 | BE | §7.4, §7.7, §11.2 | 2 | Lần thử mới có `refundId` và key mới; chỉ `ADMIN` gọi được |
| 4 | Nhánh bù trừ còn lại: phát vé vi phạm UNIQUE → lỗi nghiệp vụ → `TicketIssuanceFailed` → booking `REFUND_REQUESTED` → hoàn 100%; lỗi hạ tầng (DB mất kết nối…) → thử lại, không hoàn. Test webhook đến sai thứ tự | BE | §7.3 (dòng 7, 8), §16.3; ADR 0019 | 2 | Integration test cho cả hai loại lỗi; đối chiếu đủ mọi dòng §7.3 đều có test |
| 5 | Check-in: `POST /checkins` (organizer sở hữu sự kiện; `UPDATE … SET status = 'USED' WHERE status = 'VALID'`; ghi `check_ins`), `GET /checkin/keys` (JWKS gồm khoá `ACTIVE` và `RETIRED`); quy trình xoay khoá theo `kid` + runbook | BE | §6.4, §7.5, §11.2; BR-09; ADR 0024 | S3 | Check-in lần 2 → `TICKET_ALREADY_USED`; vé thu hồi → `TICKET_REVOKED`; sau xoay khoá vé cũ vẫn check-in được |
| 6 | Resilience4j cho Stripe (timeout 3 giây, retry chỉ khi có idempotency key, circuit breaker, bulkhead 20); metric nghiệp vụ `tickethub.*`; metric bất biến: ghế trùng (chạy truy vấn §10 định kỳ), payment kẹt, `REFUND_FAILED`, `TicketIssuanceFailed` | BE | §15.1, §16.1 (Business), §16.3; skill springbot mục 9, 11 | 2 | Test với WireMock trễ 5 giây → timeout; lỗi liên tiếp → breaker mở; metric có trên `/actuator/prometheus` |
| 6b | Thông báo trong app: `notifications`, `notification_preferences` (Flyway); consumer tạo thông báo từ `TicketsIssued`, `RefundRequested`, `RefundCompleted`, `RefundFailed` (idempotent theo `source_event_id`); `GET /me/notifications`, `unread-count`, đánh dấu đã đọc, cài đặt; loại bắt buộc không tắt được; job ShedLock xoá bản > 90 ngày | BE | §5, §6.5, §11.2, §12 | S3 | Integration test: gửi lại cùng event không tạo trùng; user chỉ đọc được thông báo của mình |
| 7 | FE hoàn vé (#9, #21, phần hoàn của #29): nút yêu cầu hoàn ở "Đơn của tôi" (hộp xác nhận ghi rõ số tiền nhận lại và phí không hoàn, ẩn khi quá mốc 48 giờ), hiển thị trạng thái hoàn; Admin: danh sách `REFUND_FAILED` + nút hoàn thủ công | FE | §14.2, §14.4 (Checkout); BR-07 | 1, 3 | Component test hộp xác nhận; Playwright: yêu cầu hoàn → vé biến khỏi "Vé của tôi" |
| 8 | FE check-in PWA (#10): ghi `docs/design/checkin.md` phần prototype chưa có (quyền camera, âm thanh, mất mạng); trang `/checkin/:eventId` quét QR bằng camera (@yudiel/react-qr-scanner), xác minh chữ ký bằng JWKS (thư viện `jose`), gọi server xác nhận; xanh / đỏ + âm thanh; mất mạng thì báo lỗi, không cho vào | FE | §7.5, §14.4 (Check-in); BR-09; ADR 0024 | 5 | Test: QR giả bị từ chối không gọi server; mất mạng → thông báo lỗi; quét được trên điện thoại thật |
| 8b | FE thông báo: chuông trong khung (popover, số chưa đọc polling 60 giây, "Đánh dấu đã đọc"), trang Thông báo (#20), cài đặt thông báo trong hồ sơ (#19) | FE | §11.2, §12; prototype #19, #20 | 6b, S1-7b | Playwright: mua vé → chuông hiện 1 → mở popover → đánh dấu đã đọc → số về 0 |
| 9 | CNPG trên k3d thay chart Postgres; object storage tương thích S3 trên staging; WAL archive + base backup hằng ngày; diễn tập PITR (C7) và kill primary (C1), mỗi kịch bản một runbook ở `docs/runbooks/` | Infra | §18, §20.3 (C1, C7); skill infra mục 7 | S3 | PITR đạt RTO ≤ 15 phút, mất ≤ 5 phút dữ liệu; failover < 30 giây |
| 10 | kube-prometheus-stack: dashboard RED, JVM, HikariCP, nghiệp vụ; Alertmanager → Telegram; alert bất biến (mức cao nhất) và alert burn rate SLO, mỗi alert có link runbook; blackbox exporter; cert-manager + alert cert sắp hết hạn (C9) | Infra | §16.2, §16.3, §20.3 (C9); skill infra mục 8 | 6, 9 | Alert giả lập tới Telegram kèm link runbook; cert ngắn hạn được gia hạn tự động |
| 11 | Tracing: Micrometer Tracing + OpenTelemetry (OTLP) ở `gateway` và `app` → Jaeger; `traceId` trong log khớp với trace. FE: Sentry + web-vitals | BE, FE, Infra | §16.1; ADR 0010 (phần log) | S3 | Một lần mua vé có trace xuyên Gateway → app trong Jaeger; lỗi JS giả lập có trong Sentry |
| 12 | Verify toàn sprint theo AC bên dưới, kèm bằng chứng | Verify | AC bên dưới | 1–11 | Báo cáo verify |
| 13 | Review cuối sprint (skill `tech-lead`; review độc lập bằng subagent cho hoàn tiền và check-in) | Review | — | 12 | APPROVED / CHANGES REQUESTED |

## Thứ tự

1. BE: 1 → 2 → (3 ‖ 4); 5, 6 và 6b song song với 1–4.
2. FE: 7 sau 1, 3 (mock trước bằng MSW); 8 sau 5; 8b sau 6b.
3. Infra: 9 → 10; 11 song song.
4. 12 → 13.

## API cho FE mock

| Endpoint | Request chính | Response chính | Mã lỗi nghiệp vụ |
|---|---|---|---|
| `POST /bookings/{id}/refund` | `Idempotency-Key` | `202 {bookingId, status: REFUND_REQUESTED, refundAmountMinor}` | `REFUND_WINDOW_CLOSED`, `BOOKING_NOT_REFUNDABLE` |
| `GET /me/bookings` | — | thêm `refundStatus`, `refundAmountMinor` | — |
| `GET /admin/bookings?status=REFUND_FAILED` | `page`, `size` | `[{bookingId, userEmail, eventTitle, totalMinor, failedAt}]` | — |
| `POST /admin/bookings/{id}/refund` | — | `202` | `BOOKING_NOT_REFUNDABLE` |
| `POST /checkins` | `{qrToken, gate}` | `{ticketId, seat, status: USED}` | `TICKET_ALREADY_USED`, `TICKET_REVOKED`, `TICKET_NOT_FOUND`, `NOT_EVENT_OWNER` |
| `GET /checkin/keys` | — | JWKS `{keys: [{kid, kty, crv, x, y}]}` | — |

## Acceptance criteria

- AC-1 (BR-07): Booking `CONFIRMED`, còn ≥ 48 giờ, vé 1.000.000đ phí 5% → khách yêu cầu hoàn → Stripe nhận lệnh hoàn 950.000đ → booking `REFUNDED` → email xác nhận.
- AC-2 (BR-07): Còn < 48 giờ → `422 REFUND_WINDOW_CLOSED`; booking chưa `CONFIRMED` → `409 BOOKING_NOT_REFUNDABLE`; booking của người khác → 404; gửi lại cùng `Idempotency-Key` → cùng response.
- AC-3 (BR-09): Ngay sau khi gửi yêu cầu, mọi vé của booking `REVOKED` (trước khi tiền hoàn xong); ghế chỉ về `AVAILABLE` sau khi mọi vé đã thu hồi.
- AC-4: Hoàn bằng thẻ test hoàn thất bại (`4000 0000 0000 5126`) → `REFUND_FAILED` + alert; Admin hoàn thủ công → bản ghi `refunds` mới với key mới → `REFUNDED`; vé vẫn `REVOKED`.
- AC-5: Giả lập vi phạm UNIQUE khi phát vé → hoàn 100% tự động + alert mức cao; giả lập DB mất kết nối khi phát vé → thử lại, phát vé thành công khi DB về, không hoàn.
- AC-6: Webhook đến sai thứ tự (vd. `payment_intent.processing` sau `succeeded`) bị bỏ qua, có log, trạng thái không đổi.
- AC-7 (BR-09): Check-in vé hợp lệ → `USED`; quét lần 2 → `TICKET_ALREADY_USED`; vé đã thu hồi → `TICKET_REVOKED`; organizer không sở hữu sự kiện → 403; QR chữ ký giả → app báo "vé giả", không gọi server; mất mạng → báo lỗi, không cho vào.
- AC-8: Sau khi xoay khoá, vé ký bằng `kid` cũ vẫn check-in được; vé phát sau đó dùng `kid` mới.
- AC-9: Stripe trễ 5 giây → lời gọi dừng ở 3 giây; lỗi liên tiếp đạt ngưỡng → circuit breaker mở, API báo lỗi ngay; lời gọi không có idempotency key không được retry.
- AC-10 (C7): Xoá nhầm dữ liệu `bookings` trên staging → khôi phục bằng PITR trong ≤ 15 phút, mất ≤ 5 phút dữ liệu; có runbook.
- AC-11 (C1): Kill Postgres primary → failover < 30 giây; tạo booking mới vẫn được; có runbook.
- AC-12: Alert bất biến giả lập (ghế trùng, payment kẹt, `REFUND_FAILED`, `TicketIssuanceFailed`) tới Telegram kèm link runbook; cert ngắn hạn được cert-manager gia hạn, alert bắn trước khi hết hạn (C9).
- AC-13: Một lần mua vé có trace xuyên Gateway → app trong Jaeger, `traceId` trong log khớp trace; lỗi JS giả lập xuất hiện trong Sentry.
- AC-14: Phát vé tạo đúng 1 thông báo "vé đã phát" cho người mua; yêu cầu hoàn tạo 1 thông báo; gửi lại cùng event không tạo thêm; `GET /me/notifications` của user khác không thấy thông báo này.
- AC-15: Chuông hiện số chưa đọc (polling ≤ 60 giây); đánh dấu đã đọc / đọc tất cả cập nhật số; tắt loại "Tạm dừng bán" trong cài đặt thì không nhận loại đó; loại vé và hoàn tiền / huỷ sự kiện không tắt được.

## Thao tác cần báo trước

Commit / push / PR / merge; `kubectl` / `helm` lên k3d; diễn tập xoá dữ liệu và PITR trên staging (C7), kill primary (C1); đặt Telegram bot token, Sentry DSN vào Secret (không commit).

## Quyết định nhỏ trong sprint

- Webhook hoàn tiền: `charge.refunded` cho thành công, `refund.failed` cho thất bại.
- App check-in xác minh chữ ký bằng thư viện `jose` (trình duyệt); server vẫn dùng Nimbus.
- Thêm `GET /admin/bookings?status=` vào §11.2 để Admin tìm booking `REFUND_FAILED`.

## Câu hỏi mở

- [BLOCKING trước việc 9] Chọn object storage tương thích S3 chạy trên staging cho backup (MinIO đã ngừng phát hành image). Claude đưa 2–3 phương án theo `solution-options` ở đầu việc 9.
- [BLOCKING trước việc 10] Bạn tạo Telegram bot (BotFather) và cho chat id.
- [BLOCKING trước việc 11] Bạn tạo tài khoản Sentry (gói free) và cho DSN.
