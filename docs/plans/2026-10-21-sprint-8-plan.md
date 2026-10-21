# Plan: Sprint 8. Phân tán và mở rộng

**Mục tiêu:** chuyển sang event-driven phân tán bằng Kafka + Avro, tách Notification thành service riêng có database riêng, không làm hỏng luồng nào của Sprint 1–7; làm phần mở rộng theo thời gian còn lại; kết thúc dự án bằng báo cáo.

**Đầu vào:** Sprint 1–7 đã xong. Mỗi hạng mục chỉ đọc đúng các § `overview.md` và ADR ở cột "Đọc".

**Cổng vào sprint:** mọi luồng Sprint 1–7 xanh (Playwright, integration test, Flash sale có phòng chờ); kết quả lưu làm mốc so sánh ở hạng mục 0.

**Ngoài sprint này:** tách thêm service khác ngoài Notification (Booking, Payment… giữ trong monolith, ADR 0029).

**Lõi và mở rộng:** hạng mục 0–12 là lõi. Hạng mục ghi **(MR)** làm theo thứ tự số, tới đâu hay tới đó; hạng mục 22–24 (báo cáo, verify, review) luôn làm, kể cả khi phần mở rộng chưa xong.

## Hạng mục

| # | Việc | Vùng | Đọc | Phụ thuộc | Xong khi |
|---|---|---|---|---|---|
| 0 | Mốc so sánh trước khi tách: chạy toàn bộ Playwright, `./mvnw verify`, Flash sale có phòng chờ trên k3d; lưu kết quả (số test, p95, số vé) | Verify | §20.1, §20.2 | — | `docs/test/sprint-8-baseline.md` |
| 1 | Kafka: compose profile `kafka` (KRaft, Schema Registry, Kafka UI có Avro); Strimzi trên k3d (3 broker, `replication.factor = 3`, `min.insync.replicas = 2`, `acks = all`), Schema Registry; topic theo §8.1 (6 partition), retry topic và `.dlt` | Infra | §8.1, §8.3, §18, §19.2; ADR 0005; skill infra | 0 | `kafka-topics --describe` đúng partition / RF; kill 1 broker → producer vẫn ghi được |
| 2 | `backend/contracts`: Avro schema cho envelope (§8.2) và mọi event ở §8.1; sinh class bằng plugin Maven; CI kiểm tương thích `BACKWARD` với registry trước khi merge | BE, Infra | §8.1, §8.2, §22; ADR 0005, 0018 | 1 | Đổi schema phá vỡ (xoá trường bắt buộc) → CI đỏ; thêm trường có default → CI xanh |
| 3 | Event externalization của Modulith: event in-process được đẩy lên topic tương ứng với key theo §8.1 (`bookingId`, `eventId`…), envelope có `eventId`, `traceId`; listener nội bộ của monolith giữ in-process | BE | §8.1–8.3; ADR 0004, 0018 | 2 | Integration test (Testcontainers Kafka): mua 1 vé → đúng các message trên `booking.events.v1`, `payment.events.v1`, `ticket.events.v1`, cùng key cho cùng booking |
| 4 | Thư viện dùng chung `backend/platform/*` khi có service thứ hai: `platform-web` (ProblemDetail, idempotency), `platform-security` (resource server, ownership), `platform-kafka` (envelope, idempotent consumer, retry / DLT), `platform-observability` (log JSON, tracing, metric); app chuyển sang dùng | BE | §22; ADR 0015 | 3 | `./mvnw verify` xanh, số test bằng mốc ở hạng mục 0 |
| 5 | `notification-service` (Maven module mới, database riêng trên CNPG): chuyển `notifications`, `notification_preferences`, `notification_log`, `templates` bằng Flyway ở service mới + script chuyển dữ liệu cũ; consumer Kafka idempotent (`processed_events`) cho mọi event Notification đang nghe; REST `/me/notifications*`, `/me/notification-preferences` và SSE stream chuyển sang service mới, Gateway định tuyến lại; lấy danh sách Admin qua API nội bộ của app bằng client `tickethub-internal`; xoá package `notification` khỏi app | BE | §5, §6.5, §12, §13.2 (`tickethub-internal`); ADR 0029 | 3, 4 | Integration test của service mới xanh; Modulith `verify()` của app xanh khi không còn package `notification`; Playwright thông báo (Sprint 4, 7) xanh không sửa test |
| 6 | Retry / DLT: retry có backoff qua topic `<topic>.retry-*`, hết lượt → `<topic>.dlt`; alert khi DLT có message (link runbook); công cụ replay từ DLT về topic gốc (script hoặc endpoint Admin nội bộ); lỗi hạ tầng được retry, lỗi dữ liệu đi thẳng DLT; thay cơ chế job `RepublishIncompleteEvents` gửi lại event lỗi không giới hạn số lần | BE, Infra | §8.1, §8.3, §16.3 | 5 | Test: message làm consumer ném lỗi → sau N lần vào DLT, alert bắn; sửa lỗi rồi replay → xử lý đúng 1 lần |
| 7 | Outbox Debezium cho service đã tách: event mới `NotificationFailed` (email gửi lỗi sau khi hết lượt thử; Admin nhận thông báo, có metric) ghi vào bảng `outbox` theo định dạng Outbox Event Router trong database của `notification-service`; Debezium Connect (profile `kafka`, KafkaConnect của Strimzi trên k3d) đẩy lên topic `notification.events.v1`. Thêm topic và event vào §8.1 | BE, Infra | §8.1, §8.3; ADR 0004, 0029 | 5 | Email lỗi → bản ghi `outbox` trong cùng transaction → message trên topic → Admin nhận thông báo; tắt Connect rồi bật lại → không mất message |
| 8 | Trace xuyên Kafka: header trace trên message Kafka, `traceId` trong envelope; một lần mua vé hiện một trace Gateway → app → Kafka → `notification-service` trong Jaeger | BE | §8.2, §16.1 | 5 | Ảnh chụp trace trong Jaeger gồm span của consumer ở service mới |
| 9 | Pact: message pact cho event app → `notification-service`, HTTP pact cho API nội bộ danh sách Admin; chạy trong CI ở cả hai phía | BE, Infra | §20.1 (Contract) | 5 | Đổi tên trường trong event phía app → Pact phía provider đỏ |
| 10 | Helm chart `notification-service` + ArgoCD Application + NetworkPolicy; tách hai Deployment cùng image: `notification-api` (REST + SSE, tối thiểu 1 replica) và `notification-worker` (consumer, KEDA theo consumer lag, **scale-to-zero**) | Infra | §17, §19.4; ADR 0028, 0029 | 5 | Không có message 5 phút → worker về 0 pod; bắn event → worker lên và xử lý hết lag |
| 11 | Chaos C3 (kill 1 broker giữa lúc mua vé → không mất message, đếm thông báo = số vé) và C10 (poison message → DLT → alert → sửa → replay); runbook + postmortem | Infra | §20.3 (C3, C10) | 6, 10 | Hai runbook ở `docs/runbooks/`, hai postmortem ở `docs/postmortems/` |
| 12 | Hồi quy sau khi tách: chạy lại mọi thứ ở hạng mục 0, so với mốc; ghi chênh lệch (độ trễ email vé p95 < 2 phút theo §16.2) | Verify | §16.2, §20.2 | 1–11 | `docs/test/sprint-8-baseline.md` có cột sau khi tách, không luồng nào đỏ |
| 13 | (MR) Elasticsearch cho tìm kiếm: projection sự kiện từ `catalog.events.v1` (tên tỉnh lưu sẵn), analyzer tiếng Việt có / không dấu, lọc thành phố, ngày, danh mục; `GET /events` dùng ES, ES chết → fallback truy vấn Postgres; dựng lại index bằng replay | BE, Infra | §6.1, §15.2, §18; ADR 0006 | 3 | Tìm "son tung" ra "Sơn Tùng"; dừng ES → `GET /events` vẫn trả kết quả (Postgres), có metric fallback |
| 14 | (MR) Vault + External Secrets: private key ký vé (ES256), Stripe key, Keycloak client secret chuyển vào Vault; Spring Cloud Vault ở dev (profile `platform`), External Secrets Operator trên k3d | Infra, BE | §6.4, §13.4; skill infra | — | Không còn secret trong values / K8s Secret tạo tay; xoay một secret trong Vault → pod nhận bản mới |
| 15 | (MR) Unleash: kill switch cho hoàn vé, phòng chờ, tìm kiếm ES; `GET /admin/feature-flags`, `POST /admin/feature-flags/{key}/toggle` (flag quan trọng bắt buộc lý do, ghi audit); FE màn #32 | BE, FE, Infra | §11.2, §15.3; prototype #32 | 13 | Tắt flag hoàn vé → `POST /bookings/{id}/refund` trả `503 FEATURE_DISABLED`; mỗi lần bật / tắt có audit |
| 16 | (MR) Argo Rollouts: canary 10% → 50% → 100% cho `app`, `gateway`, phân tích error rate và p95 từ Prometheus; chaos C6 (deploy bản cố ý lỗi → tự rollback) + runbook | Infra | §19.3, §20.3 (C6); skill infra | — | Bản lỗi dừng ở 10% và tự rollback; có runbook + postmortem C6 |
| 17 | (MR) i18n `en`: tách chuỗi còn lại, bộ dịch `en`, chọn ngôn ngữ ở header; tiền và giờ vẫn VND, `Asia/Ho_Chi_Minh` | FE | §14; skill react | — | Chuyển `en` → không còn chuỗi tiếng Việt cứng ở các màn khách (kiểm bằng script tìm chuỗi) |
| 18 | (MR) Velero: backup manifest + volume của cụm k3d lên object storage (ADR 0033), diễn tập khôi phục một namespace | Infra | §18; ADR 0033 | — | Xoá namespace `tickethub` rồi khôi phục bằng Velero, app chạy lại |
| 19 | (MR) VPA chế độ gợi ý cho `app`, `gateway`, `notification-service`; ghi gợi ý và so với request / limit hiện tại | Infra | §17 | — | Bảng gợi ý trong `docs/ops/right-sizing.md` |
| 20 | (MR) Falco: rule mặc định + 1 rule riêng (shell trong container `app`); cảnh báo về Alertmanager | Infra | §13.4 | — | `kubectl exec` shell vào pod `app` → alert tới Telegram |
| 21 | (MR) OpenCost: chi phí ước tính theo namespace / workload trên k3d, dashboard Grafana | Infra | §23 | — | Dashboard chi phí theo workload |
| 22 | Báo cáo kết thúc `docs/final-report.md`: phần xong, phần dở, nợ kỹ thuật, số liệu chính (load test, chaos, SLO), gợi ý thứ tự đọc code để học | Docs | `docs/plans/`, `docs/test/` | 12 | Có báo cáo, người dùng đã đọc |
| 23 | Verify toàn sprint theo AC bên dưới, kèm bằng chứng | Verify | AC bên dưới | 0–22 | `docs/test/sprint-8-verify.md` |
| 24 | Review cuối sprint và cuối dự án (skill `tech-lead`; review độc lập bằng subagent cho việc tách Notification và retry / DLT) | Review | — | 23 | APPROVED / CHANGES REQUESTED |

## Thứ tự

1. 0 → 1 → 2 → 3 → 4 → 5.
2. Sau 5: 6, 7, 8, 9, 10 song song; 11 sau 6 và 10; 12 sau tất cả hạng mục lõi.
3. Mở rộng theo thứ tự 13 → 21 (15 cần 13 nếu có kill switch tìm kiếm; thiếu 13 thì bỏ kill switch đó).
4. 22 → 23 → 24, làm kể cả khi phần mở rộng còn dở.

## API

FE không đổi API ở phần lõi: endpoint thông báo giữ nguyên path, Gateway chỉ đổi đích định tuyến. API mới của phần mở rộng:

| Endpoint | Request chính | Response chính | Mã lỗi nghiệp vụ |
|---|---|---|---|
| `GET /admin/feature-flags` | — | `[{key, enabled, critical, description, updatedAt, updatedBy}]` | — |
| `POST /admin/feature-flags/{key}/toggle` | `{enabled, reason?}` | `{key, enabled}` | `REASON_REQUIRED`, `FLAG_NOT_FOUND` |
| Mọi API có kill switch | — | — | `503 FEATURE_DISABLED` |

## Acceptance criteria

- AC-1 (§8.3): Mua vé ghi event vào topic đúng key; cùng booking đi cùng partition; consumer nhận lại cùng `eventId` không xử lý hai lần.
- AC-2 (§8.2): Schema phá vỡ tương thích `BACKWARD` bị CI chặn trước khi merge.
- AC-3 (C3): Kill 1 broker giữa lúc chạy mua vé → không mất message: số thông báo "vé đã phát" bằng số booking `CONFIRMED`.
- AC-4 (C10, §8.3): Poison message → retry có backoff → DLT → alert kèm runbook; sau khi sửa, replay → được xử lý đúng 1 lần.
- AC-5 (ADR 0029): `notification-service` chạy với database riêng; app không còn package `notification`; mọi luồng Sprint 1–7 xanh như mốc ở hạng mục 0; email vé p95 < 2 phút sau thanh toán.
- AC-6 (§15.2): Dừng `notification-service` → mua vé vẫn được, vé xem được trong app; bật lại → email và thông báo được gửi bù đủ.
- AC-7 (§8.3): Debezium đẩy bản ghi `outbox` lên Kafka; tắt Debezium Connect rồi bật lại không mất event.
- AC-8 (§16.1): Một lần mua vé có một trace xuyên Gateway → app → Kafka → `notification-service`.
- AC-9 (§20.1): Pact chạy ở CI hai phía; đổi hợp đồng một phía → CI đỏ.
- AC-10 (§17): `notification-worker` về 0 pod khi không có message, tự lên khi có lag.
- AC-11 (MR, ADR 0006): Tìm không dấu ra kết quả có dấu; Elasticsearch chết → tìm kiếm rơi về Postgres, đặt vé không ảnh hưởng.
- AC-12 (MR, §15.3): Tắt kill switch hoàn vé / phòng chờ / tìm kiếm có hiệu lực không cần deploy; flag quan trọng thiếu lý do → `422 REASON_REQUIRED`; mỗi thao tác có audit.
- AC-13 (MR, C6): Bản deploy cố ý lỗi bị Argo Rollouts dừng ở bước canary đầu và rollback tự động.
- AC-14: Có `docs/final-report.md` gồm phần xong, phần dở, nợ kỹ thuật, số liệu chính và thứ tự đọc code.

## Thao tác cần báo trước

Commit / push / PR / merge; `kubectl` / `helm` lên k3d (Strimzi, Schema Registry, Debezium, KEDA, Argo Rollouts, Vault, ESO, Velero, Falco, OpenCost); chạy script chuyển dữ liệu thông báo sang database mới trên staging (migration trên database không phải local / test); chaos C3, C6, C10.

## Quyết định nhỏ trong sprint

- Listener nội bộ của monolith giữ in-process (Modulith); chỉ service đã tách đọc Kafka. Kafka trong monolith chỉ dùng cho externalization.
- `notification-service` hỏi danh sách Admin qua API nội bộ của app (client `tickethub-internal`), không giữ bản sao user.
- Tách `notification-api` (luôn chạy để giữ SSE) và `notification-worker` (scale-to-zero), vì kết nối SSE không cho phép scale về 0.

- `notification-service` phát event `NotificationFailed` qua outbox Debezium (topic `notification.events.v1`), để Debezium có dữ liệu thật để đẩy.

## Câu hỏi mở

- Thứ tự phần mở rộng đang theo lộ trình (ES → Vault → Unleash → Argo Rollouts → i18n → Velero → VPA → Falco → OpenCost). Bạn muốn đổi thứ tự thì báo trước khi bắt đầu hạng mục 13.
