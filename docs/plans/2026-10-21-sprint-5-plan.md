# Plan: Sprint 5. Chịu tải và marketplace phần 1

**Mục tiêu:** chứng minh không bán trùng dưới tải (500 user / 100 ghế), có số đo trước và sau khi thêm lớp Redis; mở phần quản trị organizer và người dùng; quan sát được bằng log tập trung và diễn tập được các sự cố C2, C4, C8.

**Đầu vào:** Sprint 1–4 đã xong (giữ ghế bằng Postgres, thanh toán, vé, hoàn vé, check-in, thông báo trong app, CNPG, Prometheus / Grafana / Alertmanager, Jaeger, Sentry). Mỗi hạng mục chỉ đọc đúng các § `overview.md` và ADR ở cột "Đọc".

**Ngoài sprint này:** Stripe Connect thật, huỷ / tạm dừng sự kiện, xem audit log trên màn Admin (Sprint 6); phòng chờ, rate limit, SSE, cache sự kiện, kịch bản Spike (Sprint 7); thành viên của organizer (ngoài phạm vi dự án).

## Hạng mục

| # | Việc | Vùng | Đọc | Phụ thuộc | Xong khi |
|---|---|---|---|---|---|
| 1 | Khung load test `load-tests/jmeter/`: kịch bản Load, Flash sale, Soak, Stress (thiết kế bằng GUI, chạy `jmeter -n`, xuất HTML report); script tạo dữ liệu (500 user Keycloak test, 1 sự kiện 100 ghế `ON_SALE`); JMeter đăng nhập qua luồng Keycloak thật để lấy cookie `SESSION`. Trên staging: stripe-mock / WireMock thay Stripe, webhook giả ký đúng chữ ký bằng secret test; giới hạn CPU / RAM của `app`, `gateway` để 500 user đủ gây áp lực | Infra | §19.1, §20.2; ADR 0009; skill testcase-rule (load) | — | Chạy được Flash sale từ máy 16 GB sang staging; report HTML lưu ở `docs/test/load/` |
| 2 | **Baseline** chỉ với lớp Postgres: chạy Flash sale và Load, ghi p95 `POST /bookings`, p95 API đọc, số vé, truy vấn ghế trùng §10, số lần deadlock / retry | Infra, Verify | §10, §16.2, §20.2 | 1 | Report baseline: đúng 100 vé, 0 ghế trùng; số đo ghi vào `docs/test/load/sprint-5-load.md` |
| 3 | Lớp 1 Redis giữ ghế: Lua giữ (kiểm mọi ghế trống rồi SET cả nhóm, TTL = `expires_at` + 30 giây) và nhả bằng compare-and-delete; gia hạn TTL thêm 5 phút khi bắt đầu thanh toán; xoá key chủ động khi booking hết hạn, bị huỷ, TX Postgres thất bại; Postgres vẫn là nguồn quyết định. Key theo hash tag `{eventId}` | BE | §9, §10; BR-02, 03, 12; ADR 0020, 0021 | 2 | Integration test (Testcontainers Redis): một ghế bận → không giữ ghế nào; TX Postgres lỗi → key của đúng booking đó bị xoá, key của booking khác còn nguyên; key mất mà Postgres còn `HELD` → không bán trùng |
| 4 | Redis chết → fail-closed: health indicator cho Redis giữ ghế; khi lỗi, `POST /bookings` trả `503 SEAT_HOLD_UNAVAILABLE` (xem sự kiện vẫn chạy); Redis phục hồi → dựng lại key `seat:*` từ các dòng `HELD` (TTL theo `expires_at` còn lại) rồi mới mở lại giữ ghế; metric + alert | BE | §10 (khi Redis chết), §15.2, §16.3 | 3 | Integration test: dừng container Redis → 503, không tạo booking; bật lại → key được dựng đủ, giữ ghế chạy lại |
| 5 | Đo lại sau lớp Redis: Flash sale, Load, so với baseline (p95, số lần chạm Postgres, deadlock); viết kết luận ngắn | Verify | §20.2 | 3, 4, 11 | Bảng trước / sau trong `docs/test/load/sprint-5-load.md`; vẫn đúng 100 vé, 0 trùng |
| 6 | Organizer đăng ký: `POST /organizer/apply` (customer; trường theo form #22), `GET /organizer/application` (trạng thái hồ sơ của mình, lý do từ chối) → `organizers` `PENDING`, phát `OrganizerApplied`. Admin: `GET /admin/organizers?status=`, `approve`, `reject` (bắt buộc lý do, email kèm lý do), `bulk-approve` → `OrganizerApproved`. Account nghe `OrganizerApproved` → gán role `ORGANIZER` qua Keycloak Admin API (client `tickethub-account-admin`), cập nhật `user_profiles.roles`. Thêm `GET /organizer/application` vào §11.2 và `OrganizerApplied` vào §8.1 | BE | §6.1, §6.6, §7.6, §11.2, §13.2; ADR 0016 | — | Integration test (Testcontainers Keycloak): duyệt → user có role trong Keycloak; gửi lại `OrganizerApproved` không lỗi; customer khác không đọc được hồ sơ người khác; chỉ `ADMIN` duyệt / từ chối được |
| 7 | Admin người dùng: `GET /admin/users?q=&role=`, `GET /admin/users/{id}` (hồ sơ + đơn hàng, vé, hoàn tiền, sự kiện lấy qua interface công khai của từng module), `lock` / `unlock` (Keycloak `enabled = false` + đăng xuất mọi session, cập nhật `user_profiles.enabled`; không khoá chính mình) | BE | §6.6, §11.2, §13.2, §13.3 | — | Integration test: khoá → refresh token mất hiệu lực, đăng nhập lại bị từ chối; khoá chính mình → `422 CANNOT_LOCK_SELF`; ArchUnit / Modulith `verify()` xanh |
| 8 | Audit: Hibernate Envers cho các entity quản trị (`organizers`, `platform_fee_policies`, `user_profiles.enabled`) + bảng `audit_log` (ai, lúc nào, hành động, đối tượng, trước / sau) với API ghi dùng chung ở `shared`; ghi cho duyệt / từ chối organizer, khoá / mở khoá, hoàn thủ công (đã có từ Sprint 4) | BE | §13.4 (Audit), §6.6 | 6, 7 | Test: mỗi thao tác quản trị tạo đúng 1 dòng `audit_log` có người thao tác; Envers có revision cho thay đổi `organizers` |
| 9 | Thông báo vai trò: Admin nhận "hồ sơ organizer chờ duyệt" (nghe `OrganizerApplied`, người nhận là mọi user role `ADMIN` lấy qua Account); organizer nhận "hồ sơ được duyệt / bị từ chối" (trong app + email); idempotent theo `source_event_id` | BE | §6.5, §7.6 | 6 | Integration test: 1 hồ sơ → mỗi Admin đúng 1 thông báo; gửi lại event không tạo trùng |
| 10 | FE đăng ký organizer (#22: form, chờ duyệt, bị từ chối kèm lý do, đã duyệt; sau khi duyệt nhắc đăng nhập lại hoặc chờ refresh token để thấy menu Organizer) | FE | §7.6, §14.2; prototype #22 | 6 (mock MSW trước) | Component test 4 trạng thái; Playwright: đăng ký → Admin duyệt → user vào được `/organizer` |
| 11 | FE Admin: organizer (#15: lọc theo trạng thái, duyệt, từ chối kèm lý do, duyệt hàng loạt), người dùng (#28: tìm, lọc role, chi tiết, khoá / mở khoá có hộp xác nhận). Dùng component danh sách dùng chung | FE | §6.6, §11.2; prototype #15, #28 | 6, 7 | Playwright: duyệt hàng loạt 3 hồ sơ; khoá user → user đó bị đăng xuất ở lần refresh kế tiếp |
| 12 | Redis Sentinel trên k3d (1 master + 2 replica); Gateway (Spring Session) và app (Lettuce, Redisson) cấu hình Sentinel. PgBouncer bằng `Pooler` của CNPG; app trỏ qua Pooler, HikariCP giảm pool tương ứng | Infra | §17 (PgBouncer), §18; skill infra mục 7 | — | Kill Redis master → Sentinel bầu master mới, giữ ghế chạy lại không cần restart app; số kết nối thật tới Postgres giảm (đo trên `pg_stat_activity`) khi chạy Load |
| 13 | ELK trên k3d: Elasticsearch + Kibana (ECK hoặc chart chính thức), Filebeat đọc log JSON của pod; ILM xoá sau 7 ngày; saved search theo `traceId`, `bookingId`; link từ Grafana / Jaeger sang Kibana theo `traceId` | Infra | §16.1; ADR 0010; skill infra mục 8 | — | Tìm được toàn bộ log của 1 lần mua vé theo `traceId`; index cũ hơn 7 ngày bị xoá (kiểm bằng ILM explain) |
| 14 | Chaos Mesh trên k3d + 3 kịch bản, mỗi kịch bản: gây lỗi → alert có kêu → chẩn đoán → khắc phục → runbook ở `docs/runbooks/` + postmortem ở `docs/postmortems/`. C2: trễ 3 giây tới stripe-mock (NetworkChaos); C4: kill Redis (cả master lẫn toàn bộ Sentinel); C8: đầy disk volume Postgres (> 80%) và JVM rò bộ nhớ (endpoint chỉ bật ở profile `chaos`), lấy heap dump và phân tích | Infra | §15.2, §16.3, §20.3 (C2, C4, C8) | 4, 12, 13 | Mỗi kịch bản có alert tới Telegram, runbook và postmortem; C4 trong lúc chạy Flash sale → 0 ghế trùng |
| 15 | Soak (200 user, 1–2 giờ) và Stress (tăng tới khi gãy) trên staging; ghi điểm gãy và thành phần gãy trước | Verify | §20.2 | 5, 12 | Report Soak: heap và HikariCP ổn định; report Stress: ghi được giới hạn và cách hỏng |
| 16 | Spike Stripe Connect (test mode, platform Singapore): tạo Express account, onboarding link, `account.updated` qua endpoint Connect với secret riêng, destination charge bằng VND, hoàn với `reverse_transfer` (BR-07) và `refund_application_fee` (BR-08); kiểm từng giả định ở ADR 0022 | BE (spike) | §7.6, §7.8; ADR 0022, 0023 | — | ADR 0022 cập nhật kết quả từng giả định; nếu VND không chạy được thì có đề xuất thay thế theo `solution-options` |
| 17 | Verify toàn sprint theo AC bên dưới, kèm bằng chứng | Verify | AC bên dưới | 1–16 | `docs/test/sprint-5-verify.md` |
| 18 | Review cuối sprint (skill `tech-lead`; review độc lập bằng subagent cho lớp Redis giữ ghế và khoá tài khoản) | Review | — | 17 | APPROVED / CHANGES REQUESTED |

## Thứ tự

1. Đo trước rồi mới sửa: 1 → 2 → 3 → 4 → 5. 12 song song với 1–4 và xong trước 5.
2. Marketplace: 6 → (8 ‖ 9); 7 song song với 6. FE: 10 sau 6, 11 sau 6 và 7 (mock MSW trước).
3. 13 song song; 14 sau 4, 12, 13; 15 sau 5.
4. 16 làm sớm (Sprint 6 phụ thuộc kết quả), song song với mọi việc khác.
5. 17 → 18.

## API cho FE mock

| Endpoint | Request chính | Response chính | Mã lỗi nghiệp vụ |
|---|---|---|---|
| `POST /organizer/apply` | trường theo form #22 (`displayName`, thông tin liên hệ, pháp lý) | `201 {status: PENDING}` | `APPLICATION_ALREADY_PENDING`, `ALREADY_ORGANIZER` |
| `GET /organizer/application` | — | `{status, submittedAt, rejectionReason?}`; chưa nộp → 404 | — |
| `GET /admin/organizers?status=` | `page`, `size` | `[{id, displayName, email, status, submittedAt}]` | — |
| `POST /admin/organizers/{id}/approve` | — | `200 {status: APPROVED}` | `ORGANIZER_NOT_PENDING` |
| `POST /admin/organizers/{id}/reject` | `{reason}` | `200 {status: REJECTED}` | `ORGANIZER_NOT_PENDING` |
| `POST /admin/organizers/bulk-approve` | `{ids: []}` | `{approved: [], skipped: [{id, code}]}` | — |
| `GET /admin/users?q=&role=` | `page`, `size` | `[{userId, email, fullName, roles, enabled, lastLoginAt}]` | — |
| `GET /admin/users/{id}` | — | `{profile, bookings, tickets, refunds, events?}` | — |
| `POST /admin/users/{id}/lock`, `/unlock` | — | `200 {enabled}` | `CANNOT_LOCK_SELF` |
| `POST /bookings` (thêm lỗi) | — | — | `503 SEAT_HOLD_UNAVAILABLE` |

## Acceptance criteria

- AC-1 (BR-01): Flash sale 500 user tranh 100 ghế trong 10 giây → đúng 100 vé trong `tickets`, truy vấn ghế trùng §10 = 0 dòng; đạt ở cả baseline (chỉ Postgres) và sau khi có lớp Redis.
- AC-2: Có bảng số đo trước / sau lớp Redis: p95 `POST /bookings`, p95 API đọc, số lần deadlock / retry, số lần chạm Postgres; p95 `POST /bookings` < 800 ms và API đọc < 300 ms tại 500 user sau lớp Redis (§16.2), hoặc ghi rõ lý do nếu chưa đạt.
- AC-3 (BR-01): Giữ nhóm ghế có 1 ghế đang bận → không ghế nào trong nhóm bị giữ (Redis lẫn Postgres); TX Postgres lỗi sau khi Lua đã SET → key của đúng booking đó bị xoá, key booking khác giữ nguyên.
- AC-4 (BR-03): Key Redis sống lâu hơn hạn Postgres (`expires_at` + 30 giây; + 5 phút khi bắt đầu thanh toán); booking hết hạn hoặc bị huỷ (BR-12) → key bị xoá ngay, không chờ TTL.
- AC-5 (C4): Redis chết → `POST /bookings` trả `503 SEAT_HOLD_UNAVAILABLE`, trang sự kiện vẫn xem được, alert tới Telegram; Redis về → key dựng lại từ `HELD` trước khi mở lại giữ ghế; chạy kèm Flash sale → 0 ghế trùng.
- AC-6: Kill Redis master trên k3d → Sentinel chuyển master, giữ ghế chạy lại không cần restart pod; người đã đăng nhập giữ được session.
- AC-7: Customer nộp hồ sơ → `PENDING`, mỗi Admin nhận 1 thông báo; nộp lại khi đang chờ → `409 APPLICATION_ALREADY_PENDING`; Admin từ chối không có lý do → 400; từ chối có lý do → organizer nhận thông báo + email kèm lý do.
- AC-8: Admin duyệt → user có role `ORGANIZER` trong Keycloak và `user_profiles.roles`; sau lần refresh token kế tiếp (≤ 5 phút) vào được `/organizer`; duyệt hàng loạt bỏ qua hồ sơ không ở `PENDING` và trả danh sách bị bỏ qua.
- AC-9: Admin khoá user → user không đăng nhập được, refresh token mất hiệu lực, request sau khi access token hết hạn bị 401; Admin khoá chính mình → `422 CANNOT_LOCK_SELF`; user không phải `ADMIN` gọi API Admin → 403.
- AC-10: Mỗi thao tác duyệt / từ chối organizer, khoá / mở khoá, hoàn thủ công tạo 1 dòng `audit_log` có người thao tác, thời điểm, đối tượng, giá trị trước / sau.
- AC-11: Tìm được trong Kibana mọi dòng log của một lần mua vé theo `traceId` (Gateway và app); ILM xoá index cũ hơn 7 ngày.
- AC-12 (C2): stripe-mock trễ 3 giây → circuit breaker mở, checkout hiện lỗi rõ ràng, ghế vẫn giữ đến hết hạn; có runbook + postmortem.
- AC-13 (C8): Disk Postgres > 80% và heap tăng liên tục → alert bắn trước khi hỏng; có heap dump đã phân tích, runbook + postmortem.
- AC-14: Report Load (p95 < 300 ms, lỗi 0%), Soak (heap và pool không tăng dần trong 1 giờ), Stress (ghi điểm gãy) ở `docs/test/load/`.
- AC-15: ADR 0022 ghi kết quả từng giả định của spike Stripe Connect, gồm thu VND và transfer reversal.

## Thao tác cần báo trước

Commit / push / PR / merge; `kubectl` / `helm` lên k3d (Redis Sentinel, Pooler, ELK, Chaos Mesh); chạy chaos và load test trên staging; tạo 500 user test trong Keycloak staging; đặt secret của client `tickethub-account-admin` và Stripe test key cho spike vào Secret (không commit).

## Quyết định nhỏ trong sprint

- Thêm `GET /organizer/application` (§11.2) để FE hiện trạng thái hồ sơ; thêm event `OrganizerApplied` vào `catalog.events.v1` (§8.1).
- Redis chết khi giữ ghế: mã lỗi `503 SEAT_HOLD_UNAVAILABLE` kèm `Retry-After`.
- API ghi audit đặt ở `shared` (module nào cũng gọi được); Envers chỉ cho entity quản trị, không cho bảng nghiệp vụ nóng (`seat_inventory`, `bookings`).
- Giả định: hồ sơ bị từ chối được nộp lại (về `PENDING`); trường của form theo prototype #22, trường nào chưa có cột trong `organizers` thì thêm vào §6.1 ở hạng mục 6.

## Câu hỏi mở

- [BLOCKING trước việc 16] Bạn tạo (hoặc dùng lại) tài khoản Stripe test đăng ký ở Singapore, bật Connect, cho test secret key; tạo webhook endpoint Connect và cho signing secret.
- [BLOCKING trước việc 1] Xác nhận máy 16 GB và máy staging 32 GB cùng LAN, máy 16 GB cài được JMeter (Java 21).
- PgBouncer chạy chế độ `transaction` (gom tốt hơn, cần kiểm prepared statement của Hibernate / JDBC và advisory lock của ShedLock) hay `session` (an toàn, gom kém hơn): Claude đưa phương án theo `solution-options` ở đầu việc 12.
