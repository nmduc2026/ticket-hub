# Runbook: đốt ngân sách lỗi SLO (alert `ApiErrorBudgetBurnFast`, `ApiErrorBudgetBurnSlow`, `HikariPoolExhausted`)

SLO: tỷ lệ request không 5xx ≥ 99.5% / 30 ngày (`overview.md` §16.2). Nhanh = 2% ngân sách trong 1 giờ (critical); chậm = 10% trong 3 ngày (warning).

## Phân loại nhanh (Grafana, dashboard "Ticket Hub: RED")

1. Lỗi tập trung ở `uri` nào? Panel "Request theo status" và "Tỷ lệ lỗi 5xx".
2. Có đợt deploy mới không? `kubectl -n tickethub rollout history deploy/app`; nếu trùng thời điểm, `rollout undo` (báo trước).
3. Phụ thuộc: dashboard "HikariCP" (pool cạn, timeout lấy kết nối), Postgres (`postgres-failover.md`), Stripe (circuit breaker mở: panel "Stripe: circuit breaker"; API báo lỗi nhanh là đúng thiết kế), Redis.
4. Xem trace lỗi trong Jaeger (lọc `error=true`, service `app`) và log theo `traceId`.

## Pool cạn (`HikariPoolExhausted`)

Có request chờ kết nối khi pool đầy: tìm truy vấn chậm hoặc giao dịch giữ kết nối lâu (`pg_stat_activity`), không tăng pool bừa vì Postgres có giới hạn kết nối.

## Sau sự cố

Alert nhanh bắn thì ghi postmortem ngắn ở `docs/postmortems/`.
