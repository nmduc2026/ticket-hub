# Plan: Sprint 7. Flash sale

**Mục tiêu:** chịu được đột biến tải khi mở bán sự kiện hot: phòng chờ công bằng (BR-04), sơ đồ ghế và thông báo realtime, tự bảo vệ bằng rate limit, cache, load shedding, và autoscale kịp nhờ pre-warm.

**Đầu vào:** Sprint 1–6 đã xong (lớp Redis giữ ghế, Redis Sentinel, JMeter, Chaos Mesh, Admin / Organizer đầy đủ). Mỗi hạng mục chỉ đọc đúng các § `overview.md` và ADR ở cột "Đọc".

**Ngoài sprint này:** bật / tắt phòng chờ bằng feature flag Unleash (Sprint 8; sprint này bật theo `high_demand`); Kafka (Sprint 8: Queue nghe event Booking qua event in-process của Modulith).

## Hạng mục

| # | Việc | Vùng | Đọc | Phụ thuộc | Xong khi |
|---|---|---|---|---|---|
| 1 | Module `queue` (package `com.ndz.queue`, Modulith `verify()`): giữ `queue:{eventId}:meta` (`high_demand`, `sale_starts_at`) từ event của Catalog; `POST /queue/{eventId}/join` (customer; chỉ sự kiện `high_demand`; mở từ `sale_starts_at` − 15 phút): score ngẫu nhiên trong `[0, sale_starts_at)` nếu vào trước giờ mở bán, ngược lại score = thời điểm vào; `ZADD NX`. `GET /queue/{eventId}/status` (vị trí, thời gian chờ ước tính, `ADMITTED` nếu còn key lượt vào) | BE | §5, §7.1, §9; BR-04; ADR 0015, 0027 | — | Test: 1.000 user vào trước giờ mở bán → thứ tự khác thứ tự vào và luôn đứng trước người vào sau giờ mở bán; vào lại không đổi vị trí; sự kiện không `high_demand` → `404 QUEUE_NOT_ENABLED`; vào sớm hơn 15 phút → `409 QUEUE_NOT_OPEN` |
| 2 | Điều tiết: scheduler mỗi giây (ShedLock) cho từng sự kiện đang mở phòng chờ; số người đang ở trong = phần tử còn hạn của `queue:{eventId}:admitted` + `queue:{eventId}:pending` (bỏ phần tử quá hạn trước khi đếm); dưới `max_active` thì `ZPOPMIN` tối đa N người, tạo `queue:{eventId}:admit:{userId}` TTL 15 phút; trước giờ mở bán không gọi ai. Queue nghe `BookingCreated` (score = `expires_at` + ân hạn) / `BookingConfirmed` / `BookingExpired` / `BookingCancelled` để giữ ZSET `pending`. `max_active`, N cấu hình được | BE | §7.1, §9; BR-03, 04; ADR 0027 | 1 | Integration test: `max_active = 50` → tối đa 50 người ở trong; người bỏ đi (lượt vào hết hạn, booking hết hạn) thì người kế tiếp được gọi; mất một event Booking thì số đếm tự lành khi phần tử hết hạn |
| 3 | Booking kiểm lượt vào (bước 0 ở §10): sự kiện `high_demand` thì `POST /bookings` cần key lượt vào còn hạn, xoá key khi tạo booking thành công (1 lượt 1 booking); thiếu → `403 ADMISSION_REQUIRED`. Booking gọi interface công khai của `queue` | BE | §7.1, §10; BR-04; ADR 0018, 0027 | 2 | Test: một lượt vào tạo booking thứ hai → 403; booking lỗi (ghế bận) thì lượt vào còn nguyên để chọn lại |
| 4 | SSE: `GET /queue/{eventId}/stream` (vị trí, `ADMITTED`), `GET /events/{id}/seats/stream` (trạng thái ghế: Booking publish thay đổi lên `seat-updates:{eventId}`, giữ `seatstatus:{eventId}`), `GET /me/notifications/stream` (Notification publish lên `user-notify:{userId}`: thông báo mới + số chưa đọc). `SseEmitter` trên virtual threads, heartbeat 15 giây, Gateway chuyển SSE không buffer và không timeout sớm | BE | §9, §12, §13.1; ADR 0001, 0011 | 2 | Test: 2 pod, client nối pod A nhận được thay đổi ghế sinh ra ở pod B; kết nối lại lúc đã được gọi tên → nhận `ADMITTED` ngay; SSE thông báo chỉ đẩy cho đúng user |
| 5 | Rate limit ở Gateway bằng Bucket4j trên Redis: theo user (`rl:{userId}`) và IP (`rl:ip:{ip}`), giới hạn riêng cho `POST /bookings`, `POST /queue/*/join`, API đọc; vượt → `429 RATE_LIMITED` + `Retry-After`; Redis lỗi → **fail-open** (bỏ qua giới hạn, log, metric, alert) | BE | §9, §13.4, §15.2 (Redis); skill springbot | — | Test: vượt giới hạn → 429 có `Retry-After`; dừng Redis → request vẫn qua, metric `ratelimit_bypassed` tăng, alert bắn |
| 6 | Cache chi tiết sự kiện `cache:event:{eventId}` (TTL 5 phút + jitter; chỉ 1 request rebuild bằng lock, các request khác trả bản cũ); xoá cache khi nhận `EventUpdated` / `EventSuspended` / `EventResumed` / `EventCancelled` | BE | §9 (cache stampede) | — | Test: 200 request đồng thời khi cache trống → đúng 1 lần đọc DB; sửa sự kiện → lần đọc sau thấy bản mới |
| 7 | Load shedding ở Gateway (`503 OVERLOADED` + `Retry-After` khi số request đang xử lý vượt ngưỡng); graceful shutdown cho `app` và `gateway` (ngừng nhận request mới, xử lý xong request đang chạy, đóng SSE để client nối sang pod khác; `preStop` + `terminationGracePeriodSeconds`); metric `events_opening_soon` (số sự kiện mở bán trong 15 phút tới) | BE, Infra | §15.3, §17; ADR 0028 | 4 | Test: vượt ngưỡng → 503 có `Retry-After`, DB không tăng kết nối; rolling restart trong lúc chạy Load → 0 lỗi 5xx ngoài 503 có chủ đích |
| 8 | FE phòng chờ (#4): vị trí, thời gian chờ ước tính, trạng thái trước giờ mở bán (bốc thăm), tự chuyển sang chọn ghế khi `ADMITTED`, đồng hồ lượt vào 15 phút; SSE đứt → gọi `status` rồi nối lại; gọi `GET /me` mỗi 5 phút; trang chi tiết sự kiện (#3) chuyển nút "Mua vé" sang "Vào phòng chờ" cho sự kiện `high_demand` | FE | §7.1, §14.4 (Phòng chờ); BR-04; prototype #3, #4 | 1, 4 (mock MSW trước) | Component test các trạng thái; Playwright (mock SSE): nhận `ADMITTED` → tự sang `/events/:id/seats`; tắt mạng rồi bật lại → vẫn giữ lượt vào |
| 9 | FE realtime: sơ đồ ghế (#5) cập nhật màu theo SSE, kết nối lại thì tải snapshot `GET /events/{id}/seats` rồi nghe tiếp; chuông và trang thông báo nhận qua SSE, rơi về polling 60 giây khi SSE lỗi; organizer cấu hình phòng chờ ở #24 (`high_demand`, khoá sau publish theo BR-14); bảng điều khiển sự kiện (#25) tự làm mới; các trạng thái lỗi còn lại ở #23 (`429`, `503`, `ADMISSION_REQUIRED`, `SEAT_HOLD_UNAVAILABLE`) | FE | §12, §14.4; BR-14; prototype #5, #23, #24, #25 | 4, 5, 7 | Playwright: hai trình duyệt, A giữ ghế → ghế đổi màu ở B trong ≤ 2 giây; SSE bị cắt → chuông vẫn cập nhật bằng polling |
| 10 | KEDA trên k3d cho `gateway` và `app`: trigger CPU + Prometheus (request / giây, p95 latency); pre-warm khi `events_opening_soon > 0` (min replica cao hơn); không khai báo HPA riêng; kiểm `startupProbe`, `readinessProbe`, `PodDisruptionBudget` | Infra | §17; ADR 0028; skill infra (KEDA) | 7 | Tạo sự kiện mở bán sau 10 phút → replica tăng trước giờ mở bán; tải giảm → scale-in không làm rớt SSE quá 1 lần kết nối lại |
| 11 | Load test có phòng chờ: Spike (10 → 500 user trong 5 giây) và Flash sale (500 user / 100 ghế) với sự kiện `high_demand`; chaos C5 (spike 500 user) có runbook + postmortem | Verify, Infra | §20.2, §20.3 (C5) | 2–10 | Report ở `docs/test/load/sprint-7-load.md`: hệ thống không sập, p99 < 1 giây, đúng 100 vé, 0 trùng |
| 12 | Verify toàn sprint theo AC bên dưới, kèm bằng chứng | Verify | AC bên dưới | 1–11 | `docs/test/sprint-7-verify.md` |
| 13 | Review cuối sprint (skill `tech-lead`; review độc lập bằng subagent cho phòng chờ và lượt vào) | Review | — | 12 | APPROVED / CHANGES REQUESTED |

## Thứ tự

1. Phòng chờ: 1 → 2 → 3; 4 sau 2.
2. Bảo vệ: 5, 6 song song với 1–4; 7 sau 4.
3. FE: 8 sau 1, 4; 9 sau 4, 5, 7 (mock MSW / SSE trước).
4. Infra: 10 sau 7; 11 sau mọi việc trên.
5. 12 → 13.

## API cho FE mock

| Endpoint | Request chính | Response chính | Mã lỗi nghiệp vụ |
|---|---|---|---|
| `POST /queue/{eventId}/join` | — | `202 {position, estimatedWaitSeconds, phase: LOTTERY \| QUEUED \| ADMITTED}` | `QUEUE_NOT_ENABLED`, `QUEUE_NOT_OPEN` |
| `GET /queue/{eventId}/status` | — | `{position, estimatedWaitSeconds, phase, admissionExpiresAt?}` | `QUEUE_NOT_JOINED` |
| `GET /queue/{eventId}/stream` | SSE | `event: position {position, estimatedWaitSeconds}`, `event: admitted {admissionExpiresAt}`, heartbeat | — |
| `GET /events/{id}/seats/stream` | SSE | `event: seats {changes: [{seatId, status}]}` | — |
| `GET /me/notifications/stream` | SSE | `event: notification {…}`, `event: unread {count}` | — |
| `POST /bookings` (thêm lỗi) | — | — | `403 ADMISSION_REQUIRED`, `429 RATE_LIMITED` |
| Mọi API (Gateway) | — | — | `429 RATE_LIMITED`, `503 OVERLOADED` (kèm `Retry-After`) |

## Acceptance criteria

- AC-1 (BR-04): Phòng chờ chỉ mở cho sự kiện `high_demand`, từ 15 phút trước giờ mở bán; trước giờ mở bán không ai được gọi vào.
- AC-2 (BR-04): Người vào trong 15 phút trước giờ mở bán được xếp ngẫu nhiên và đứng trước mọi người vào sau giờ mở bán; người vào sau xếp theo thời điểm vào; vào lại (F5, tab mới) không đổi vị trí.
- AC-3 (BR-04): Lượt vào hạn 15 phút; một lượt vào chỉ tạo được 1 booking (lần hai → `403 ADMISSION_REQUIRED`); booking lỗi vì ghế bận thì lượt vào vẫn còn; muốn mua tiếp phải xếp hàng lại.
- AC-4 (BR-04): SSE đứt đúng lúc được gọi tên → kết nối lại hoặc gọi `status` nhận `ADMITTED` ngay, không mất lượt; khách chờ 40 phút vẫn không phải đăng nhập lại khi được gọi.
- AC-5: Số người đang ở trong không vượt `max_active`; khách bỏ đi (lượt vào hoặc booking hết hạn) thì người kế tiếp được gọi trong ≤ 2 nhịp scheduler.
- AC-6 (§12): Thay đổi ghế ở pod bất kỳ tới mọi client đang xem sơ đồ trong ≤ 2 giây; kết nối lại thì sơ đồ khớp snapshot.
- AC-7: Thông báo mới tới chuông qua SSE không cần tải lại trang; SSE lỗi → chuông vẫn cập nhật bằng polling 60 giây; user chỉ nhận thông báo của mình.
- AC-8 (§13.4): Vượt rate limit → `429 RATE_LIMITED` + `Retry-After`; Redis chết → rate limit fail-open, có log, metric và alert.
- AC-9: Cache sự kiện: 200 request đồng thời khi cache trống → 1 lần đọc DB; sửa sự kiện → bản mới hiện trong lần đọc kế tiếp.
- AC-10 (§15.3): Vượt ngưỡng → `503 OVERLOADED` + `Retry-After`; rolling restart lúc đang có tải → không có lỗi 5xx khác, client SSE tự nối sang pod khác.
- AC-11 (§17): Có sự kiện mở bán trong 15 phút tới → KEDA giữ số replica tối thiểu cao hơn trước giờ mở bán; hết sự kiện sắp mở → về mức thường.
- AC-12 (§20.2, C5): Spike 10 → 500 user trong 5 giây khi bật phòng chờ → không sập, p99 < 1 giây; Flash sale qua phòng chờ → đúng 100 vé, 0 ghế trùng; có runbook + postmortem C5.

## Thao tác cần báo trước

Commit / push / PR / merge; `kubectl` / `helm` lên k3d (KEDA, cấu hình Gateway); chạy load test và chaos C5 trên staging.

## Quyết định nhỏ trong sprint

- `POST /queue/{eventId}/join` trả `phase`: `LOTTERY` (trước giờ mở bán, chưa có vị trí cố định), `QUEUED`, `ADMITTED`.
- Ngưỡng mặc định (cấu hình được, chỉnh theo kết quả load test): `max_active` 200 / sự kiện, gọi tối đa 20 người / giây; rate limit `POST /bookings` 5 lần / phút / user, API đọc 20 request / giây / IP.
- Bảng điều khiển sự kiện (#25) tự làm mới bằng polling 30 giây (§12 không có stream cho số liệu); nếu bạn muốn realtime thật thì thêm một stream SSE ở hạng mục 4.

- `max_active` và tốc độ gọi là cấu hình chung của hệ thống; organizer chỉ bật `high_demand` ở #24.
