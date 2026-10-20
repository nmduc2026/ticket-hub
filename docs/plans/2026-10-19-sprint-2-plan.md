# Plan: Sprint 2. Bán vé phần 1: sự kiện và giữ ghế

**Mục tiêu:** organizer mở bán được sự kiện; khách xem, chọn và giữ được ghế, không bao giờ trùng; chạy bản Sprint 1 trên AWS lần đầu.

**Đầu vào:** mỗi hạng mục chỉ đọc đúng các § `overview.md` và ADR ở cột "Đọc"; plan Sprint 1 (code đã có).

**Ngoài sprint này:** thanh toán, vé, email (Sprint 3); phòng chờ, lượt vào, Redis giữ ghế (Sprint 5, 7); Admin CRUD địa điểm / sơ đồ ghế / import (Sprint 6); Stripe Connect, `charges_enabled` thật (Sprint 6). Sự kiện trong sprint này đều `high_demand = false`.

## Hạng mục

| # | Việc | Vùng | Đọc | Phụ thuộc | Xong khi |
|---|---|---|---|---|---|
| 1 | Schema `catalog` (Flyway): `organizers`, `administrative_units` (CHECK cấp / loại), `venues`, `seat_maps`, `sections`, `seats`, `events`, `ticket_tiers`, `tier_sections` (UNIQUE `event_id, section_id`), `platform_fee_policies`. Seed: 1 tỉnh + vài phường, 1 địa điểm, 1 sơ đồ ghế (~500 ghế, 3 khu), organizer `APPROVED` + `payout_ready = true` gắn user test ORGANIZER, mức phí 500 bps | BE | §6.1; BR-05, 06 | S1 | Migration chạy trên Testcontainers; test CHECK đơn vị hành chính và UNIQUE `tier_sections` |
| 2 | Catalog API cho organizer: tạo / sửa / xem sự kiện của mình, CRUD hạng vé, gán hạng vé cho khu (`PUT tier-sections`), chọn địa điểm / sơ đồ ghế (`GET /venues`, `/venues/{id}/seat-maps`). Kiểm quyền sở hữu; sau publish chỉ sửa thông tin hiển thị | BE | §6.1, §11.2, §13.3; BR-14 | 1 | Test IDOR (organizer khác → 404), customer gọi → 403; sửa trường bị khoá sau publish → `EVENT_LOCKED` |
| 3 | Upload ảnh banner: `POST /organizer/events/{id}/banner` (multipart, ≤ 2 MB, jpg / png / webp) → S3. Thêm S3Mock vào compose `core`; thêm endpoint vào `overview.md` §11.2 | BE, Infra | §6.1, §21.1 (Lưu file); skill infra mục 3 | 2 | Upload được và xem lại được ảnh; file sai loại / quá cỡ → 422 |
| 4 | Publish và vòng đời sự kiện: kiểm BR-05 (organizer `APPROVED` + `payout_ready`), khoá sơ đồ (`locked`), chốt `platform_fee_bps` theo mức phí hiện hành, phát `EventPublished`; máy trạng thái sự kiện; job `ON_SALE` (tới `sale_starts_at` và `inventory_ready`) / `ENDED` (ShedLock); nghe `SeatInventoryReady` → `inventory_ready = true` | BE | §7.7 (Sự kiện), §7.9; BR-05, 06, 14; ADR 0021, 0023 | 2 | Test từng chuyển trạng thái hợp lệ và một chuyển không hợp lệ; chưa `inventory_ready` thì không `ON_SALE` |
| 5 | Schema `booking`: `seat_inventory`, `bookings` (+ unique index pending), `booking_items`, `event_snapshots`, `processed_events`. Nghe event của catalog → cập nhật `event_snapshots`; nghe `EventPublished` → lấy danh sách ghế qua interface công khai của catalog → tạo `seat_inventory` idempotent → `SeatInventoryReady` | BE | §6.2, §7.9, §8.3 (idempotent consumer); ADR 0018, 0021 | 4 | Publish → `seat_inventory` đúng số ghế của các khu có hạng vé; nhận event 2 lần → kết quả như 1 lần |
| 6 | API công khai: `GET /events` (lọc từ khoá, thành phố, ngày, danh mục bằng Postgres; phân trang), `GET /events/{id}`, `GET /events/{id}/seats` (sơ đồ từ catalog + trạng thái từ `seat_inventory`) | BE | §5, §6.1, §11.2, §15.2 (dòng Elasticsearch) | 5 | Guest gọi được không cần đăng nhập; khu không gán hạng vé trả về là "không bán" |
| 7 | Giữ ghế `POST /bookings` (bắt buộc `Idempotency-Key`): kiểm sự kiện `ON_SALE` theo `event_snapshots`, BR-02 (≤ 6 ghế, 1 booking pending, tổng vé ≤ `max_tickets_per_user`), khoá theo thứ tự `seat_id`, `UPDATE … WHERE status = 'AVAILABLE'`, deadlock thử lại 1 lần; `expires_at` = now + 10 phút; lưu `contact_email`; phát `BookingCreated`. `GET /bookings/{id}` (chủ booking) | BE | §6.2, §6.7, §7.2 (đến bước 201), §10 (lớp 2); BR-01, 02, 03; ADR 0020, 0025 | 5 | Test 50 luồng tranh 1 ghế → đúng 1 thành công; hai booking chồng ghế khác thứ tự không treo; test biên BR-02 |
| 8 | Hết hạn và huỷ: job `ExpireBookings` (30 giây, ShedLock, hạn theo `expires_at` / `payment_started_at` + ân hạn) → `EXPIRED`, nhả ghế, `BookingExpired`; `DELETE /bookings/{id}` (BR-12) → `CANCELLED`, nhả ghế, `BookingCancelled`; máy trạng thái booking | BE | §7.3 (2 dòng đầu), §7.7 (Booking); BR-03, 12 | 7 | Test với `Clock` cố định: quá hạn → `EXPIRED`, ghế `AVAILABLE`; có `payment_started_at` thì chưa hết hạn trong 5 phút ân hạn |
| 9 | FE công khai: trang chủ, danh sách / tìm kiếm sự kiện, chi tiết sự kiện theo màn #1–3 (mock MSW theo mục API khi BE chưa xong) | FE | §14.2, §14.3 | S1 | Đủ trạng thái loading / empty / error; lọc theo thành phố, ngày |
| 9b | Component danh sách dùng chung (README §3): toolbar ghim dưới topbar (tìm, bộ lọc, Đặt lại \| Cột, Xuất, nút Tạo), bộ lọc facet phương án A (chọn nhiều / chọn một, ghi lên URL), dropdown và combobox có ô tìm, bảng + phân trang + trạng thái loading / empty / không có kết quả / lỗi, chọn dòng + nút hàng loạt kèm số | FE | README §3; prototype #12 (mẫu), #34; skill react mục 10 | S1-7b | Component test bộ lọc (chọn tất cả 3 trạng thái, ghi URL, Đặt lại); Playwright: tải lại trang giữ bộ lọc, cuộn thì toolbar ghim |
| 10 | Đối chiếu prototype màn #5, #6 (phần giữ ghế), #23; ghi `docs/design/seat-map.md` chỉ phần prototype chưa thể hiện: luồng lỗi API, polling trạng thái ghế, thao tác bàn phím trên canvas | FE | §14.4; BR-02, 03, 12; ADR 0008 | — | Người dùng duyệt phần bổ sung |
| 11 | Component sơ đồ ghế react-konva: zoom / pan, màu theo hạng vé và trạng thái, khu không bán màu xám, chọn tối đa theo BR-02 | FE | §14.4; ADR 0008; skill react mục 9 | 10 | Render mượt ~500 ghế; component test chọn / bỏ chọn, chặn ghế thứ 7 |
| 12 | Luồng giữ ghế trên FE: gửi `POST /bookings` (sinh `Idempotency-Key` một lần mỗi lần bấm), đồng hồ đếm ngược theo `expiresAt`, nút "Huỷ và chọn lại ghế", map mã lỗi sang thông báo tiếng Việt | FE | §14.4 (Checkout); BR-02, 03, 12 | 7, 8, 11 | Playwright: chọn 2 ghế → thấy đồng hồ; huỷ → ghế trống lại |
| 13 | FE organizer: danh sách sự kiện của tôi (#12, dùng component 9b), modal tạo sự kiện + sửa sự kiện (#24, upload banner), CRUD hạng vé (#13) | FE | §14.2 (Organizer); BR-14 | 2, 3 | Trường bị khoá sau publish hiển thị chỉ đọc |
| 14 | FE organizer: gán hạng vé cho khu trên sơ đồ, publish (hiện lỗi BR-05 nếu có) | FE | §7.9; BR-05, 06 | 4, 11, 13 | Playwright: tạo sự kiện → gán khu → publish → thấy sự kiện ở trang công khai |
| 15 | AWS lab 1 (`infra/terraform/aws/lab1/`): EC2 chạy image Sprint 1 bằng compose (gateway, app, Keycloak, Redis), RDS Postgres, S3 + CloudFront (frontend ở `/`; `/api/*`, `/oauth2/*`, `/login/*`, `/logout`, `/auth/*` về EC2 để cùng domain), CloudWatch; `verify-clean.sh` | Infra | §23; ADR 0017; skill infra mục 9 | S1 (image trên GHCR, kết quả thăm dò Free plan) | Người dùng apply → đăng nhập được qua CloudFront → destroy → `verify-clean.sh` sạch |
| 16 | Verify toàn sprint theo AC bên dưới, kèm bằng chứng (skill `verification`) | Verify | AC bên dưới | 1–15 | Báo cáo verify; truy vấn số ghế `HELD` / `SOLD` trùng booking = 0 |
| 17 | Review cuối sprint (skill `tech-lead`; review độc lập bằng subagent cho SQL giữ ghế) | Review | — | 16 | APPROVED / CHANGES REQUESTED |

## Thứ tự

1. BE: 1 → 2 → (3 ‖ 4) → 5 → (6 ‖ 7) → 8.
2. FE song song với BE nhờ MSW: 9, 9b và 10 bắt đầu ngay; 11 sau 10; 13 sau 2 và 9b; 12 và 14 khi BE tương ứng xong.
3. 15 làm bất cứ lúc nào (chỉ cần kết quả Sprint 1).
4. 16 → 17.

## API cho FE mock

| Endpoint | Request chính | Response chính | Mã lỗi nghiệp vụ |
|---|---|---|---|
| `GET /events` | `q`, `cityId`, `from`, `to`, `category`, `page`, `size` | danh sách `{id, title, bannerUrl, startsAt, venueName, cityName, minPriceMinor, currency, status}` | — |
| `GET /events/{id}` | — | chi tiết + hạng vé `{id, name, priceMinor}` + `maxTicketsPerUser` | — |
| `GET /events/{id}/seats` | — | `sections[{code, name, shape, tierId?}]`, `seats[{id, sectionCode, row, number, x, y, status}]` | — |
| `POST /bookings` | `{eventId, seatIds[]}` + `Idempotency-Key` | `201 {bookingId, expiresAt, totalMinor, currency}` | `EVENT_NOT_ON_SALE`, `SEAT_UNAVAILABLE`, `MAX_SEATS_EXCEEDED`, `PENDING_BOOKING_EXISTS`, `TICKET_LIMIT_EXCEEDED` |
| `GET /bookings/{id}` | — | `{id, status, expiresAt, items[], totalMinor}` | — |
| `DELETE /bookings/{id}` | — | `204` | `BOOKING_NOT_CANCELLABLE` |
| `/organizer/events…` | theo §11.2 | — | `EVENT_LOCKED` (BR-14), `PUBLISH_NOT_ALLOWED` (BR-05) |

## Acceptance criteria

- AC-1 (BR-05, BR-14): Organizer đã duyệt tạo sự kiện, hạng vé, gán khu, publish → `PUBLISHED`. Sau publish, sửa giờ diễn / giá / sơ đồ → `EVENT_LOCKED`; sửa tiêu đề, mô tả, banner, danh mục → thành công.
- AC-2 (BR-06): Sự kiện publish ghi `platform_fee_bps` = mức phí hiện hành (500).
- AC-3 (§7.9): Sau publish, `seat_inventory` có đúng số ghế của các khu có hạng vé, khu không gán không có ghế nào; `inventory_ready = true`; tới `sale_starts_at` → `ON_SALE`. Giả lập chưa tạo xong tồn kho → không `ON_SALE`.
- AC-4: Organizer khác sửa sự kiện → 404; customer gọi `/organizer/**` → 403; chưa đăng nhập → 401.
- AC-5: Guest xem danh sách, tìm theo từ khoá và thành phố, xem chi tiết và sơ đồ ghế có trạng thái; khu không bán hiển thị màu xám, không chọn được.
- AC-6 (BR-01, BR-02): Giữ ghế trống → `201` kèm `expiresAt`; ghế đã bị giữ → `409 SEAT_UNAVAILABLE`; 7 ghế → `422 MAX_SEATS_EXCEEDED`; đã có booking chờ thanh toán cho sự kiện → `409 PENDING_BOOKING_EXISTS`; vượt `max_tickets_per_user` (tính cả `PENDING_PAYMENT` và `CONFIRMED`) → `422 TICKET_LIMIT_EXCEEDED`; sự kiện chưa `ON_SALE` → `409 EVENT_NOT_ON_SALE`.
- AC-7 (§6.7): Gửi lại `POST /bookings` cùng `Idempotency-Key` → cùng response, không tạo booking mới; cùng key khác body → `422 IDEMPOTENCY_KEY_REUSED`.
- AC-8 (BR-01): 50 luồng cùng giữ 1 ghế → đúng 1 thành công, 49 nhận `SEAT_UNAVAILABLE`; không ghế nào được 2 booking giữ.
- AC-9 (BR-03): Booking quá 10 phút (dùng `Clock`) → `EXPIRED`, ghế về `AVAILABLE`, người khác giữ được.
- AC-10 (BR-12): Khách huỷ booking `PENDING_PAYMENT` → `CANCELLED`, ghế `AVAILABLE` ngay; huỷ booking của người khác → 404; huỷ booking đã `EXPIRED` → `409 BOOKING_NOT_CANCELLABLE`.
- AC-11: Lab AWS: mở frontend qua CloudFront, đăng nhập được bằng user test; sau destroy `verify-clean.sh` không còn tài nguyên.
- AC-12: Danh sách sự kiện của organizer: lọc trạng thái + địa điểm và từ khoá ghi lên URL, tải lại trang giữ nguyên; toolbar ghim khi cuộn; chọn dòng hiện "Xuất (n)"; màn khớp prototype #12.

## Thao tác cần báo trước

Commit / push / PR / merge; `terraform apply` / `destroy` lab 1 (hỏi từng lần).

## Quyết định nhỏ trong sprint

- `GET /events/{id}/seats` do module `booking` sở hữu: lấy sơ đồ qua interface công khai của catalog (người dùng đang chờ, ADR 0018), trạng thái từ `seat_inventory`.
- Upload banner đi qua app (multipart), không dùng presigned URL: đơn giản, đủ cho ảnh nhỏ.
- Mã lỗi nghiệp vụ như bảng "API cho FE mock"; thêm vào `lib/error-messages.ts` ở FE.

## Câu hỏi mở

- Không có câu hỏi chặn. Kích thước sơ đồ seed (~500 ghế, 3 khu) là giả định; đổi được khi làm việc 1.
