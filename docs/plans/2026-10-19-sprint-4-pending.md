# Việc còn treo: Sprint 4

Ghi những việc chưa xong hoặc cần người dùng làm. Xoá dòng khi xong. Nhánh `feature/sprint-4` (tách từ `feature/sprint-3`), mỗi hạng mục một commit.

## Tiến độ

| Hạng mục | Trạng thái |
|---|---|
| 1–4 (hoàn vé, Payment hoàn tiền, Admin hoàn thủ công, nhánh bù trừ) | Xong, test xanh |
| 5 (check-in, JWKS, runbook xoay khoá) | Xong, test xanh |
| 6, 6b (Resilience4j + metric, thông báo trong app) | Xong, test xanh |
| 7, 8, 8b (FE hoàn vé, check-in, thông báo) | Xong, test xanh; chưa thử camera thật |
| 9 (CNPG) | Đã viết manifest + runbook; **chưa chạy trên cụm** |
| 10 (Prometheus, alert, cert-manager) | Đã viết manifest, rule, dashboard, runbook (`docs/ops/observability.md`); **chưa chạy trên cụm**, chờ Telegram token + chat id |
| 11 (tracing, Sentry) | Code BE/FE + Jaeger manifest xong, test xanh; **chưa thấy trace trong Jaeger, chưa gửi lỗi tới Sentry**, chờ cụm + Sentry DSN |
| 12 (báo cáo verify theo AC) | Xong phần chạy được trên máy này: `docs/test/sprint-4-verify.md` |
| 13 (review cuối sprint) | Xong, review nội tuyến (không dùng subagent): APPROVED kèm ghi chú, xem cuối báo cáo verify |

Bằng chứng đã chạy: backend `./mvnw verify` 327 test xanh; frontend `vitest` 128 test xanh, `tsc` và `oxlint` sạch, Playwright mock (`refund`, `checkin`, `notifications`) qua ở 1280px và 390px.

## Cần người dùng

| # | Việc | Vì sao treo | Cách làm |
|---|---|---|---|
| 1 | Chọn object storage S3 cho backup Postgres | BLOCKING trước khi backup chạy được (hạng mục 9) | Đọc `docs/adr/0033-backup-object-storage.md` (đề xuất Garage), trả lời chọn phương án; Claude viết manifest dựng storage |
| 2 | Tạo Telegram bot (BotFather) và cho chat id | BLOCKING hạng mục 10 | Telegram → BotFather → `/newbot`; đặt token vào Secret, không commit |
| 3 | Tạo tài khoản Sentry (gói free) và cho DSN | BLOCKING hạng mục 11 | sentry.io; DSN đặt vào biến môi trường FE, không commit |
| 4 | Tạo tài khoản Stripe test và đặt `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` (còn từ Sprint 3) | Chưa chạy hoàn tiền với Stripe thật | Xem `docs/plans/2026-10-19-sprint-3-pending.md` mục 1 |
| 5 | Đăng ký thêm sự kiện `charge.refunded` và `refund.failed` cho endpoint webhook Stripe | App chỉ xử lý hai loại này cho hoàn tiền | Dashboard Stripe → Webhooks, hoặc `stripe listen --events payment_intent.*,charge.refunded,refund.failed` |

## Chưa kiểm chứng (cần Stripe thật hoặc cụm)

| Hạng mục | Việc | Cần |
|---|---|---|
| 2 | Hoàn tiền với Stripe thật: thẻ `4000 0000 0000 5126` phải ra `refund.failed`. Hình dạng payload `charge.refunded` / `refund.failed` (`payment_intent`, `failure_reason`) đang dựa trên tài liệu, test dùng WireMock | Stripe test key + `stripe listen` |
| 2 | Số tiền 950.000 gửi lên Stripe đã kiểm bằng WireMock; chưa kiểm trên Stripe thật | Như trên |
| 8 | Quét QR bằng camera điện thoại thật, âm thanh, rung; thử Safari iOS (cần HTTPS) | Điện thoại + HTTPS |
| 9 | Dựng CNPG, WAL archive, base backup, diễn tập PITR (C7: RTO ≤ 15 phút, mất ≤ 5 phút) và kill primary (C1: < 30 giây). Chưa có k3d/cụm trên máy này | Cài k3d, báo trước các lệnh `kubectl`/`helm` |
| 9 | `kubectl --dry-run=server` cho YAML mới (`postgres/cluster.yaml`, 2 Application ArgoCD); version chart `cloudnative-pg` 0.26.0 chưa xác nhận trên mạng | Cụm |
| 9 | Tên Service và cổng giữ chỗ `backup-s3.tickethub.svc:3900` trong `cluster.yaml` phải đổi theo phương án chọn ở mục 1 | Mục 1 |
| 10 | Chưa chạy `helm template`/`kubectl --dry-run=server` cho `kube-prometheus-stack` (75.15.1), `prometheus-blackbox-exporter` (11.1.1): hai version chart đặt theo trí nhớ, chưa xác nhận trên mạng. Rule PromQL chưa qua `promtool check rules` | Cụm hoặc `helm search repo` + `promtool` |
| 10 | Tên metric Prometheus (`tickethub_*_total`, `hikaricp_*`, `http_server_requests_seconds_*`), nhãn `job="app"`, tên Service blackbox, label `app.kubernetes.io/name: app` của ServiceMonitor chưa đối chiếu với `/actuator/prometheus` thật trên cụm | Cụm |
| 10 | Alert giả lập tới Telegram (AC-12), cert 1 giờ tự gia hạn (C9), alert burn rate chưa chạy. `chat_id: 0` trong `kube-prometheus-stack.yaml` là giữ chỗ | Mục 2 + cụm |
| 11 | Trace Gateway → app trong Jaeger chưa thấy (AC-13). Test chỉ chứng minh app nối `traceparent` vào log; chưa chứng minh Gateway (MVC proxy) tự gắn `traceparent` khi chuyển tiếp. Image `jaegertracing/jaeger:2.9.0` và cờ `--set` cho OTLP chưa kiểm | Cụm; nếu Gateway không gắn, thêm propagation ở proxy |
| 11 | Lỗi JS giả lập trong Sentry chưa thấy (AC-13); `VITE_SENTRY_DSN` chưa có | Mục 3 |

## Việc kỹ thuật còn lại

| # | Việc | Ghi chú |
|---|---|---|
| 1 | Chạy lại hạng mục 9, 10, 11 trên cụm khi có k3d và các mục ở "Cần người dùng" | Xem bảng tiến độ |
| 2 | `monitoring.enablePodMonitor` của CNPG đã bật (việc 10); sync lỗi nếu thứ tự wave khiến CRD PodMonitor chưa có thì sync lại | Cụm |
| 3 | PgBouncer (`Pooler`) cho CNPG | Chưa thêm; HikariCP của app đã gom kết nối |
| 4 | Bản sao backup ra ngoài máy staging | Backup nằm cùng cụm nên mất máy là mất backup |
| 5 | Thông báo loại "Tạm dừng bán" (`SALE_PAUSED`) và "Sắp hết giờ giữ ghế" (`HOLD_EXPIRING`) mới có cài đặt, chưa có nơi phát sinh | Cần consumer `EventSuspended` (xác định ai nhận) và job báo sắp hết giờ giữ ghế |
| 6 | Thông báo cho Organizer / Admin (khách yêu cầu hoàn, hoàn tiền lỗi) và SSE `/me/notifications/stream` | SSE làm ở Sprint 7; Admin hiện chỉ thấy qua log, metric và trang `/admin/orders` |
| 7 | Email theo cài đặt `email` của loại tuỳ chọn | Chưa có loại tuỳ chọn nào gửi email, nên cờ `email` mới chỉ lưu |
| 8 | FE đơn hết hạn rồi mới nhận thanh toán: chưa hiện "không giữ lại được ghế, sẽ hoàn tiền" (tồn từ Sprint 3) | Cần API báo `BookingConfirmationFailed` / hoàn tiền cho khách |
| 9 | Mở lại trang thanh toán sau khi Stripe đã nhận tiền mà chưa `CONFIRMED` vẫn hiện form (tồn từ Sprint 3) | Cần `GET` trạng thái payment |
| 10 | Huỷ sự kiện và hoàn hàng loạt (BR-08): `RefundRequested` đã có lý do `EVENT_CANCELLED` ở tài liệu nhưng chưa có producer | Sprint 6 |
| 11 | `TicketCheckedIn` (có trong bảng event §8) chưa phát; check-in chưa chặn theo thời gian sự kiện | Phát khi cần cho thống kê / thông báo |
| 12 | Event listener lỗi hạ tầng được gửi lại bởi job `RepublishIncompleteEvents` (30 giây một lần) không giới hạn số lần, chưa có DLT | Kafka retry + DLT ở Sprint 8 |
| 13 | `/actuator/prometheus` mở không cần token ở cổng của `app`; Gateway chỉ định tuyến `/api/**` nên không ra ngoài | Đã thêm NetworkPolicy `allow-monitoring-ingress` (việc 10); chưa chạy trên cụm |
| 14 | Admin hoàn thủ công (`POST /admin/bookings/{id}/refund`) không bắt `Idempotency-Key` | Đã chặn bằng trạng thái (`REFUND_FAILED` → `REFUND_REQUESTED`), gọi lần hai trả 409 |
| 15 | `spotless:check` chưa chạy cho code mới; một số file mới là LF trong khi repo có file CRLF (Windows) | Xem pending Sprint 2 mục 11 |
| 16 | Dọn mục đã xong ở pending Sprint 3: mục "Việc kỹ thuật" 3 (Resilience4j) và 7 (UNIQUE → hoàn) | Làm khi đóng sprint |

## Quyết định nhỏ đã tự chọn (để duyệt)

- `RefundRequested` mang `reason` và `refundAmountMinor`; Payment tự tính lại số tiền từ payment đã thu (khách yêu cầu: trừ `application_fee_minor`; lý do khác: hoàn 100%) và chỉ cảnh báo khi lệch.
- Nhả ghế sau hoàn đếm `TicketRevoked` theo từng vé trong bảng `booking.revoked_tickets`; yêu cầu hoàn đến trước khi vé kịp phát thì vé sinh ra đã `REVOKED` (bảng `ticket.revoked_bookings`).
- Phát vé gặp ghế đã có vé chưa thu hồi: không nhả ghế của booking đó (ghế đang thuộc vé khác), hoàn 100% gồm phí.
- `GET /me/bookings` thêm `ticketCount`, `platformFeeMinor`, `refundableAmountMinor`, `refundDeadline` để FE hiện hộp xác nhận hoàn mà không tự tính tiền.
- Metric `tickethub.booking.held` (không phải `created`) vì Prometheus coi hậu tố `_created` là dành riêng.
- Màn Admin `/admin/orders` hiện chỉ gồm danh sách `REFUND_FAILED` (đủ cho hạng mục 7); chưa làm các bộ lọc khác của màn #29.
- FE check-in không có số đếm "312/1.200" và tên người mua của prototype vì API chưa trả (ghi ở `docs/design/checkin.md`).
- Job gửi lại event treo tắt trong test (`tickethub.events.republish-interval=24h`); test gọi `IncompleteEventPublications` trực tiếp.
