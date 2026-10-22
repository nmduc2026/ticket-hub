# Runbook: Gateway từ chối request (quá tải) hoặc bỏ qua rate limit

Alert: `GatewayShedding` (cảnh báo), `RateLimitBypassed` (nghiêm trọng). Thiết kế: `overview.md` §13.4, §15.2, §15.3.

## GatewayShedding: Gateway trả `503 OVERLOADED`

Số request đang xử lý (`tickethub_gateway_inflight`) vượt ngưỡng (ghi 500, đọc 350). Đây là hành vi bảo vệ có chủ đích,
không phải lỗi: webhook Stripe, health và `POST /payments` không bao giờ bị từ chối.

1. Xem `tickethub_gateway_shed_total{kind}` và `tickethub_gateway_inflight` trên Grafana: tải thật hay một request chậm
   giữ chỗ?
2. Nếu tải thật: KEDA đã tăng bản sao chưa (`kubectl get hpa -n tickethub`)? Đã tới `maxReplicaCount` thì tăng giới hạn
   hoặc chấp nhận hàng đợi (phòng chờ vẫn hoạt động).
3. Nếu một request chậm: kiểm p95 của `app` và pool kết nối (`HikariPoolExhausted`), Postgres, Redis.
4. Sau sự cố: ngưỡng chỉnh ở `tickethub.gateway.load-shedding.*` theo kết quả load test.

## RateLimitBypassed: Redis lỗi, rate limit đang bỏ qua (fail-open)

`tickethub_gateway_ratelimit_bypassed_total` tăng: Gateway không đọc được Redis nên không giới hạn tốc độ. Hệ thống vẫn
phục vụ, nhưng bot / spam không bị chặn.

1. Redis Sentinel còn master không? Làm theo `docs/runbooks/seat-hold-unavailable.md` (cùng cụm Redis).
2. Trong lúc chờ: theo dõi `GatewayShedding`; load shedding vẫn bảo vệ `app` vì không phụ thuộc Redis.
3. Redis về lại thì metric ngừng tăng sau tối đa 5 giây (khoảng bỏ qua sau lỗi).
