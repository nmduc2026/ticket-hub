# Plan: Sprint 6. Marketplace phần 2, AWS lab 2 và gỡ AWS

**Mục tiêu:** organizer nhận tiền qua Stripe Connect; Admin đủ công cụ vận hành (phí, tạm dừng, huỷ và hoàn hàng loạt, đối soát, danh mục); organizer có dashboard doanh thu; lab AWS cuối chạy k3s trên EC2 và tài khoản AWS sạch trước **11/12/2026**.

**Đầu vào:** Sprint 1–5 đã xong (gồm duyệt organizer, audit log, kết quả spike Stripe Connect trong ADR 0022). Mỗi hạng mục chỉ đọc đúng các § `overview.md` và ADR ở cột "Đọc".

**Cổng vào sprint:** Flash sale 500 user / 100 ghế → đúng 100 vé, 0 trùng; mọi luồng Sprint 1–5 xanh trên k3d.

**Ngoài sprint này:** thành viên của organizer (#27 phần thành viên, ngoài phạm vi dự án); realtime ở bảng điều khiển sự kiện (#25, Sprint 7); feature flag (#32, Sprint 8).

**Lõi và mở rộng:** hạng mục ghi **(MR)** là mở rộng, làm sau lõi theo thứ tự số; thiếu thời gian thì phần (MR) rơi trước. AWS lab 2 nằm ở lõi và làm sớm, không để cuối sprint.

## Hạng mục

| # | Việc | Vùng | Đọc | Phụ thuộc | Xong khi |
|---|---|---|---|---|---|
| 1 | Stripe Connect onboarding: `POST /organizer/stripe/onboarding-link` (tạo Express account nếu chưa có, lưu `connected_accounts`), `GET /organizer/stripe/account` (trạng thái `none / incomplete / pending / active / restricted`, mục còn thiếu, số dư), `POST /organizer/stripe/login-link` (Express Dashboard). Webhook `POST /webhooks/stripe-connect` với signing secret riêng, dedupe theo `stripe_event_id`; `account.updated` đổi `charges_enabled` → `OrganizerPayoutStatusChanged` → Catalog cập nhật `organizers.payout_ready`. Thêm 2 endpoint mới vào §11.2 | BE | §6.3, §7.6, §11.2, §13.1 (webhook), §13.4; BR-05; ADR 0022 | — | Integration test: webhook Connect sai chữ ký / dùng secret của endpoint platform → 400; `charges_enabled` false → true → `payout_ready = true`, publish được; event trùng không xử lý lại |
| 2 | Destination charge: PaymentIntent có `transfer_data.destination` + `application_fee_amount`; Payment kiểm `charges_enabled` mỗi lần tạo PaymentIntent (`409 ORGANIZER_PAYOUT_NOT_READY`). Hoàn tiền có `reverse_transfer = true`; lý do `EVENT_CANCELLED` thêm `refund_application_fee = true`. Sự kiện tạo trước khi có Connect (dữ liệu seed) chạy tiếp theo charge trên platform | BE | §7.8, §15.1; BR-07, 08, 10; ADR 0022, 0023, 0026 | 1 | Test với WireMock kiểm body gửi Stripe: vé 1.000.000đ phí 5% → `application_fee_amount = 50000`; hoàn BR-07 → 950.000 + `reverse_transfer`; hoàn BR-08 → 1.000.000 + `refund_application_fee` |
| 3 | Admin phí nền tảng (BR-06): `GET /admin/platform-fees` (lịch sử), `POST /admin/platform-fees` (`feeBps`, `effectiveFrom` ≥ hiện tại); publish chốt mức đang hiệu lực; ghi audit | BE | §6.1, §11.2; BR-06; ADR 0023 | — | Test: đổi phí 5% → 7% từ ngày D; sự kiện publish trước D giữ 5%, sau D nhận 7% |
| 4 | Tạm dừng bán (BR-13): `OrganizerPayoutStatusChanged{false}` hoặc Admin đình chỉ organizer (`POST /admin/organizers/{id}/suspend`) → mọi sự kiện `PUBLISHED` / `ON_SALE` của organizer → `SUSPENDED` → `EventSuspended`; Admin tạm dừng thủ công (`suspend` kèm lý do, `bulk-suspend`), mở lại (`resume`: về `ON_SALE` nếu đã qua `sale_starts_at`, ngược lại `PUBLISHED`). Booking cập nhật `event_snapshots`, từ chối giữ ghế mới. Thông báo: khách có booking của sự kiện ("tạm dừng bán"), organizer, Admin ("sự kiện tự tạm dừng"); ghi audit | BE | §6.2, §6.5, §7.7 (Sự kiện), §11.1 (hàng loạt), §11.2; BR-13 | 1 | Integration test: mất `charges_enabled` → mọi sự kiện đang bán của organizer `SUSPENDED`, vé đã bán giữ nguyên, `POST /bookings` → `409 EVENT_NOT_ON_SALE`; `bulk-suspend` trả kết quả theo từng id |
| 5 | Huỷ sự kiện (BR-08): `POST /admin/events/{id}/cancel` (lý do) → `CANCELLED` → `EventCancelled`; Booking huỷ mọi `PENDING_PAYMENT`, phát `RefundRequested(reason = EVENT_CANCELLED)` cho từng booking `CONFIRMED`; Payment đưa vào hàng đợi hoàn tiền (bảng hàng đợi + job ShedLock), giới hạn tốc độ cấu hình được (mặc định 10 lần / giây, dưới bulkhead 20 của §15.1), lỗi tạm thời thử lại có backoff; thanh toán đến sau của booking đã huỷ → hoàn 100%. `GET /admin/events/{id}/refund-progress` (đã hoàn / đang chờ / thất bại). Thông báo bắt buộc cho khách (trong app + email) | BE | §7.3, §7.4 (huỷ sự kiện), §7.7, §15.1; BR-08, 09; ADR 0026 | 2 | Test 1.000 booking với Stripe mock: đủ 1.000 bản ghi `refunds`, không trùng, không sót; tốc độ gọi Stripe không vượt giới hạn; kill pod giữa chừng rồi chạy lại vẫn đủ và không hoàn hai lần |
| 6 | `sales_by_event`: consumer `BookingConfirmed` / booking `REFUNDED` (idempotent qua `processed_events`) cập nhật `sold_count`, `revenue_minor`, `refunded_*`; đếm số đã vào cổng từ `TicketCheckedIn` (đã phát từ sprint dọn dẹp). Organizer: `GET /organizer/events/{id}/sales` (jOOQ: theo hạng vé, theo ngày, tỉ lệ lấp đầy, số đã check-in), `GET /organizer/dashboard?range=`, `GET /organizer/bookings?eventId=&status=&from=&to=&q=` (+ CSV) | BE | §6.2, §8.1, §11.1 (CSV), §11.2, §13.3; skill springbot (jOOQ) | — | Integration test: 3 booking xác nhận + 1 hoàn → số liệu khớp truy vấn đếm thẳng từ `bookings`; organizer A gọi sự kiện của organizer B → 404 |
| 7 | Admin vận hành: `GET /admin/dashboard?range=` (doanh thu gộp, phí thu được, vé bán, sự kiện đang bán, danh sách "cần xử lý", sự kiện sắp mở bán), `GET /admin/events?status=&organizerId=&from=&to=&q=`, `GET /admin/bookings?status=&organizerId=&needsReconcile=&q=` mở rộng từ Sprint 4 (+ chi tiết có dòng thời gian Stripe); CSV theo §11.1 (≤ 10.000 dòng, ghi audit); thông báo vận hành cho Admin: hoàn tiền lỗi, đơn lệch khi đối soát, Stripe của organizer bị hạn chế | BE | §6.5, §11.1, §11.2 | 4, 5 | Test: "cần xử lý" đếm đúng từng loại; CSV quá 10.000 dòng → `422 EXPORT_TOO_LARGE`; mỗi lần xuất có 1 dòng audit |
| 8 | AWS lab 2 (Terraform `infra/terraform/aws/lab-2/`): k3s trên EC2 Spot trong Auto Scaling Group sau ALB, Cluster Autoscaler theo ASG, RDS PostgreSQL Multi-AZ (diễn tập reboot with failover), SES sandbox gửi email vé, S3 thật cho ảnh banner (thay S3 giả), SSM Parameter Store cho secret, CloudWatch Alarm + SNS → email; deploy chart Helm hiện có lên k3s. Dịch vụ bị chặn ở Free plan thì dùng phương án thay thế ở §23.1. Xong lab: `terraform destroy` + `verify-clean.sh` | Infra | §17 (Node), §19.4, §23; ADR 0013, 0017; skill infra (Terraform, chi phí) | 2 (image mới) | Mua được 1 vé trên k3s ở AWS; tăng tải → ASG thêm node; RDS failover rồi app chạy tiếp; sau destroy `verify-clean.sh` sạch |
| 9 | Gỡ AWS lần cuối (sau hạng mục 8, **trước 11/12/2026**): lưu tài liệu / ảnh cần giữ vào `docs/ops/aws-lab-2.md`, `aws-nuke` dry-run rồi chạy thật, `verify-clean.sh`, kiểm Cost Explorer hôm sau; ghi lịch kiểm tra định kỳ tới khi credits hết hạn | Infra | §23.1, §23.4 (lớp 5) | 8 | `verify-clean.sh` và Cost Explorer sạch; ghi ngày gỡ vào `docs/ops/aws-lab-2.md` |
| 10 | FE organizer phần tiền: kết nối Stripe (#26: 5 trạng thái theo README §5 "Luồng kết nối Stripe", 4 bước, Express Dashboard, chặn ngắt kết nối), cảnh báo "Chưa thể publish → Kết nối Stripe" ở #24 | FE | §7.6; BR-05; prototype #26, #24; `docs/design/README.md` §5 | 1 | Component test 5 trạng thái; Playwright (mock): `none` → bấm kết nối → gọi onboarding-link → về `pending` |
| 11 | FE organizer phần số liệu: tổng quan (#11), bảng điều khiển sự kiện (#25: doanh thu theo ngày, lấp đầy theo hạng vé, số check-in, banner khi bị tạm dừng; Recharts theo skill `dataviz`), đơn hàng (#14: lọc, xuất CSV; bộ lọc facet thêm "Tuỳ chọn khoảng ngày" theo README §3) | FE | §14.4 (Dashboard); prototype #11, #14, #25 | 6 | Component test biểu đồ với dữ liệu MSW; số trên màn khớp response; tiền hiển thị VND đúng định dạng |
| 12 | FE Admin vận hành: tổng quan (#35), sự kiện (#34: side sheet chi tiết, tạm dừng / mở lại / huỷ có gõ "HUY", tạm dừng hàng loạt, tiến độ hoàn tiền), đơn hàng & đối soát (#29 đầy đủ: lọc, chi tiết dòng thời gian Stripe, hoàn thủ công đã có từ Sprint 4), phí (#30 mức mặc định) | FE | §11.2; BR-06, 08, 13; prototype #29, #30, #34, #35 | 3, 4, 5, 7 | Playwright (mock): huỷ sự kiện → thanh tiến độ tăng tới 100%; tạm dừng hàng loạt hiện kết quả từng sự kiện |
| 13 | Verify lõi (hạng mục 1–12) theo AC, kèm bằng chứng | Verify | AC bên dưới | 1–12 | `docs/test/sprint-6-verify.md` phần lõi |
| 14 | (MR) Audit log: `GET /admin/audit-logs?actor=&action=&from=&to=` + màn #31 (side sheet so sánh trước / sau) | BE, FE | §13.4; prototype #31 | — | Lọc theo người thao tác ra đúng các dòng; xuất CSV có audit |
| 15 | (MR) Địa điểm và đơn vị hành chính: CRUD `/admin/venues` (+ `bulk-delete`, chỉ xoá địa điểm chưa có sự kiện), CRUD `/admin/administrative-units` (không xoá cứng đơn vị còn con / còn địa điểm, chỉ `is_active = false`), `GET /administrative-units` công khai có cache; FE #16, #33 (chưa gồm import) | BE, FE | §6.1 (đơn vị hành chính), §11.2; prototype #16, #33 | — | Test: xoá đơn vị còn địa điểm → `409 ADMIN_UNIT_IN_USE`; `venues.ward_id` trỏ cấp 1 → 422 |
| 16 | (MR) Spike `generateSeats` (tối đa 1 ngày): hàm thuần TypeScript + bản Java cho khu `GRID` (xoay, cong, ẩn ghế, đánh số), test cùng bộ dữ liệu cho cả hai bản ra cùng toạ độ. Quá 1 ngày chưa ổn → lùi về "form + lưới" theo ADR 0030 và báo người dùng | BE, FE | §6.1 (sơ đồ ghế); ADR 0030 | — | Bộ test chung (JSON input → toạ độ) xanh ở cả Vitest và JUnit |
| 17 | (MR) Sơ đồ ghế phía BE: CRUD `/admin/seat-maps` (sections, `seat_map_elements`), lưu thì sinh lại `seats` bằng `generateSeats`, khoá sửa đồng thời bằng Redisson `lock:seatmap:{id}`, sơ đồ `locked` không sửa được, `POST /admin/seat-maps/{id}/copy` tạo `version` mới | BE | §6.1, §9, §11.2; ADR 0030 | 16 | Test: sửa sơ đồ `locked` → `409 SEAT_MAP_LOCKED`; hai Admin lưu cùng lúc → một bên `409 SEAT_MAP_BEING_EDITED`; sự kiện đã publish vẫn đọc bản cũ sau khi copy |
| 18 | (MR) FE editor sơ đồ ghế (#17): chọn, kéo, xoay bước 15°, snap lưới, phím mũi tên, nhân bản khu, undo / redo, sân khấu / lối đi / nhãn, ẩn ghế, xem như khách; cache theo khu trên konva, tắt hit-test từng ghế khi kéo; màn chọn ghế của khách (#5) vẽ đúng khu xoay và khu `IMPORTED` (`seatGeometry.sectionRect` hiện bỏ qua `rotation`) | FE | §14.4; ADR 0008, 0030; prototype #17 | 16, 17 | Dựng lại sơ đồ mẫu trong prototype; sơ đồ 20.000 ghế kéo khu không giật (≥ 30 fps, đo bằng Performance panel) |
| 19 | (MR) Import Excel: `GET /admin/imports/templates/{type}`, `POST /admin/imports/{type}?dryRun=` (tổng / hợp lệ / lỗi theo dòng–cột), `GET /admin/imports/{id}/errors` (file lỗi `.xlsx`); `import_jobs`, file lưu S3; 3 loại `administrative-units`, `venues`, `seat-maps` (khu `IMPORTED`); FE modal import ở #16, #17, #33 | BE, FE | §6.1 (`import_jobs`), §11.2; `docs/design/README.md` §7 | 15, 17 | Test: file mẫu ở README §7 import được; file có lỗi → dry-run trả đúng dòng–cột, không ghi gì; import lại cùng file khớp theo `code`, không tạo trùng |
| 20 | (MR) Cấu hình thanh toán chỉ đọc: `GET /admin/payment-config`, `GET /admin/stripe-events?failed=true`, `POST /admin/stripe-events/{id}/reprocess` (idempotent theo `stripe_event_id`), `POST /admin/stripe/webhook-check`; FE #36 | BE, FE | §11.2, §13.4; prototype #36 | 1 | Test: reprocess một event đã xử lý không đổi trạng thái; webhook-check trả mã phản hồi của endpoint |
| 21 | (MR) Organizer: nhân bản sự kiện (`duplicate`, `bulk-duplicate`, không chép giờ mở bán), gửi lại email vé hàng loạt (`bulk-resend-tickets`, ≤ 100 id), hồ sơ `GET/PUT /organizer/profile` (đổi pháp lý → Admin duyệt lại), FE cài đặt #27 (hồ sơ, thông báo, pháp lý; không gồm thành viên) | BE, FE | §11.1 (hàng loạt), §11.2; prototype #12, #14, #27 | 6 | Test: bản nhân bản là `DRAFT`, không có `sale_starts_at`; gửi lại vé cho đơn không phải của mình → bị bỏ qua kèm lý do |
| 22 | Verify phần mở rộng đã làm, cập nhật `docs/test/sprint-6-verify.md` | Verify | AC bên dưới | 14–21 | Báo cáo verify đầy đủ |
| 23 | Review cuối sprint (skill `tech-lead`; review độc lập bằng subagent cho dòng tiền Connect và hoàn hàng loạt) | Review | — | 13, 22 | APPROVED / CHANGES REQUESTED |

## Thứ tự

1. Tiền: 1 → 2 → 5; 3 song song; 4 sau 1.
2. AWS: 8 bắt đầu ngay khi 2 có image; 9 ngay sau 8, không muộn hơn 11/12/2026.
3. Số liệu và vận hành: 6 song song với 1–5; 7 sau 4, 5.
4. FE: 10 sau 1; 11 sau 6; 12 sau 3, 4, 5, 7 (mock MSW trước).
5. 13 (verify lõi) trước khi mở phần mở rộng.
6. Mở rộng theo thứ tự 14 → 21; 16 → 17 → 18; 19 sau 15 và 17.
7. 22 → 23.

## API cho FE mock

| Endpoint | Request chính | Response chính | Mã lỗi nghiệp vụ |
|---|---|---|---|
| `POST /organizer/stripe/onboarding-link` | — | `{url, expiresAt}` | `ORGANIZER_NOT_APPROVED` |
| `GET /organizer/stripe/account` | — | `{state, chargesEnabled, payoutsEnabled, requirements: [], balanceMinor?, currency}` | — |
| `POST /organizer/stripe/login-link` | — | `{url}` | `STRIPE_NOT_CONNECTED` |
| `GET/POST /admin/platform-fees` | `{feeBps, effectiveFrom}` | `[{id, feeBps, effectiveFrom, createdBy}]` | `FEE_OUT_OF_RANGE`, `EFFECTIVE_FROM_IN_PAST` |
| `POST /admin/events/{id}/suspend`, `/resume`, `/cancel` | `{reason}` | `{status}` | `EVENT_STATE_INVALID` |
| `POST /admin/events/bulk-suspend` | `{ids, reason}` | `{results: [{id, ok, code?}]}` | — |
| `POST /admin/organizers/{id}/suspend` | `{reason}` | `{status: SUSPENDED, suspendedEvents}` | `ORGANIZER_STATE_INVALID` |
| `GET /admin/events/{id}/refund-progress` | — | `{total, refunded, pending, failed}` | — |
| `GET /admin/dashboard?range=` | — | `{grossMinor, feeMinor, ticketsSold, eventsOnSale, todo: {pendingOrganizers, reconcile, refundFailed, suspendedEvents, stripeRestricted}, openingSoon: []}` | — |
| `GET /admin/events?…`, `GET /admin/bookings?…` | lọc, `page`, `size`; `Accept: text/csv` để xuất | danh sách | `EXPORT_TOO_LARGE` |
| `GET /organizer/dashboard?range=` | — | `{revenueMinor, ticketsSold, byDay: [], recentBookings: []}` | — |
| `GET /organizer/events/{id}/sales` | — | `{byTier: [{tierId, sold, capacity, revenueMinor}], byDay: [], checkedIn}` | — |
| `GET /organizer/bookings?…` | lọc, `page`, `size`; CSV | danh sách | `EXPORT_TOO_LARGE` |
| `POST /webhooks/stripe-connect` | raw body, `Stripe-Signature` | `200` | — |

Endpoint của hạng mục (MR) theo §11.2; mã lỗi ghi ở cột "Xong khi".

## Acceptance criteria

- AC-1 (BR-05): Organizer đã duyệt nhưng chưa `charges_enabled` → publish trả `409 PUBLISH_NOT_ALLOWED` với `reasons` có `PAYOUT_NOT_READY`; `account.updated` bật `charges_enabled` → publish được.
- AC-2: Webhook Connect chỉ chấp nhận chữ ký bằng secret của endpoint Connect; sai chữ ký, lệch thời gian > 5 phút → 400; cùng `stripe_event_id` gửi lại → không xử lý lại.
- AC-3 (§7.8): Mua vé 1.000.000đ, phí 5% → PaymentIntent có `transfer_data.destination` của organizer và `application_fee_amount = 50000`; organizer mất `charges_enabled` giữa chừng → `POST /payments` trả `409 ORGANIZER_PAYOUT_NOT_READY`.
- AC-4 (BR-07, §7.8): Khách hoàn → Stripe nhận hoàn 950.000 với `reverse_transfer = true`, không hoàn phí.
- AC-5 (BR-08, §7.8): Admin huỷ sự kiện → mọi booking `CONFIRMED` hoàn 100% gồm phí (`refund_application_fee = true`), không xét mốc 48 giờ; booking `PENDING_PAYMENT` → `CANCELLED`; thanh toán đến sau của booking đó → hoàn 100%; vé bị thu hồi (BR-09); khách nhận thông báo không tắt được.
- AC-6 (BR-08): Huỷ sự kiện có 1.000 booking với Stripe mock → đúng 1.000 lệnh hoàn, mỗi booking đúng một lần thành công; tốc độ gọi Stripe ≤ giới hạn cấu hình; kill pod giữa chừng → chạy tiếp không trùng không sót; `refund-progress` đếm khớp.
- AC-7 (BR-13): Organizer mất `charges_enabled` hoặc bị Admin đình chỉ → mọi sự kiện `PUBLISHED` / `ON_SALE` của họ `SUSPENDED`, vé đã bán giữ nguyên, giữ ghế mới bị từ chối, Admin nhận thông báo; Admin mở lại → về `ON_SALE` nếu đã qua giờ mở bán, ngược lại `PUBLISHED`.
- AC-8 (BR-13): Admin tạm dừng thủ công không có lý do → 400; `bulk-suspend` 3 sự kiện trong đó 1 đã `ENDED` → 2 thành công, 1 lỗi `EVENT_STATE_INVALID`; mọi thao tác có audit.
- AC-9 (BR-06): Đổi phí chỉ áp cho sự kiện publish sau `effectiveFrom`; sự kiện đã publish giữ `platform_fee_bps` cũ.
- AC-10: `sales_by_event` khớp với truy vấn đếm trực tiếp sau khi có booking xác nhận và hoàn; gửi lại cùng event không cộng hai lần; organizer chỉ xem được số liệu, đơn hàng của sự kiện mình (khác → 404).
- AC-11: CSV xuất đúng bộ lọc đang chọn, `ids=` chỉ xuất dòng đã chọn, > 10.000 dòng → `422 EXPORT_TOO_LARGE`, mỗi lần xuất có audit.
- AC-12: Admin dashboard: "cần xử lý" đếm đúng từng loại (hồ sơ chờ duyệt, lệch đối soát, `REFUND_FAILED`, sự kiện tạm dừng, Stripe bị hạn chế); bấm vào mở đúng danh sách đã lọc.
- AC-13 (AWS): Trên k3s ở AWS mua được 1 vé và nhận email qua SES; tăng tải → ASG thêm node do Cluster Autoscaler; RDS reboot with failover → app chạy tiếp sau khi kết nối lại; sau destroy `verify-clean.sh` sạch.
- AC-14 (AWS): Trước 11/12/2026: `aws-nuke` đã chạy, `verify-clean.sh` sạch, Cost Explorer không có chi phí phát sinh mới.
- AC-15 (MR): Sơ đồ ghế `locked` không sửa được, copy ra `version` mới; toạ độ ghế FE và BE sinh từ cùng tham số giống nhau; editor dựng được sơ đồ mẫu có sân khấu, khu xoay, hàng cong, lối đi.
- AC-16 (MR): Import Excel dry-run báo lỗi theo dòng–cột và không ghi dữ liệu; import thật khớp theo `code`, chạy lại không tạo trùng; tải được file lỗi.
- AC-17 (MR): Reprocess event Stripe idempotent; webhook-check trả mã phản hồi thật; audit log lọc được theo người thao tác, hành động, thời gian.

## Thao tác cần báo trước

Commit / push / PR / merge; `kubectl` / `helm` lên k3d; **`terraform apply` lab 2 (hỏi từng lần, không áp dụng "skip")**, `terraform destroy`, `aws-nuke`; deploy lên k3s ở AWS; đặt Stripe Connect webhook secret, SES, SSM vào Secret / Parameter Store (không commit).

## Quyết định nhỏ trong sprint

- Thêm `GET /organizer/stripe/account` và `POST /organizer/stripe/login-link` vào §11.2 để màn #26 hiện đủ trạng thái và mở Express Dashboard.
- Hàng đợi hoàn tiền khi huỷ sự kiện là bảng trong schema `payment` + job ShedLock (không dùng Kafka, vì Kafka vào ở Sprint 8); giới hạn tốc độ mặc định 10 lần / giây, cấu hình được.
- Sự kiện seed trước khi có Connect vẫn thu trên tài khoản platform; sự kiện publish sau Sprint 6 bắt buộc Connect (BR-05).
- Phí nền tảng chỉ có một mức chung (BR-06); "mức phí riêng" theo organizer trên màn #30 ngoài phạm vi, ghi chú lên prototype.
- Tạm dừng bán (BR-13) chỉ chặn giữ ghế mới, không có nhánh xử lý riêng cho booking đang `PENDING_PAYMENT`: đơn đó thanh toán được nếu kiểm `charges_enabled` lúc tạo PaymentIntent vẫn qua (§7.6), không thì tự hết hạn và nhả ghế như thường. Ghi một câu vào BR-13; không viết test riêng cho tổ hợp này ngoài test của kiểm `charges_enabled` đã có.

## Câu hỏi mở

- [BLOCKING trước việc 1] Kết quả spike Sprint 5: nếu platform Singapore không thu được VND với connected account Express ở test mode, chọn phương án thay thế trong ADR 0022 (vd. đổi tiền tệ test sang SGD / USD, giữ VND ở hiển thị).
- [BLOCKING trước việc 8] AWS: bạn đăng nhập `aws sso login` và xác nhận Free plan còn credits; Claude báo kết quả thăm dò dịch vụ (Spot, ALB, Multi-AZ, SES) trước khi apply.
