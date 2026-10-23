# Load test với JMeter

Kịch bản Load, Flash sale, Soak, Stress cho `overview.md` §20.2 (ADR 0009). Một plan duy nhất `ticket-hub.jmx`, chọn kịch bản bằng `-Jscenario`. **Trạng thái: bản nháp, chưa chạy thật** (chờ cụm staging; mục "Việc treo của Sprint 5" ở `docs/plans/2026-10-21-sprint-cleanup-plan.md`).

## Chuẩn bị (một lần)

1. Máy chạy tải (16 GB) cài JDK 21 và Apache JMeter 5.6.x; cùng LAN với máy staging (32 GB).
2. Dữ liệu test (chỉ trên Keycloak / Postgres của staging hoặc dev):
   ```bash
   KEYCLOAK_URL=https://auth.tickethub.localhost KEYCLOAK_ADMIN_PASSWORD=... sh scripts/seed-users.sh 500 users.csv
   psql "$DATABASE_URL" -v event_id=$(uuidgen) -f scripts/seed-event.sql
   psql "$DATABASE_URL" -c "\copy (SELECT event_id, seat_id FROM booking.seat_inventory WHERE event_id = '<event_id>') TO 'seats.csv' CSV HEADER"
   ```
   `users.csv` và `seats.csv` chứa tài khoản test, đã nằm trong `.gitignore` của thư mục này, không commit.
3. Staging: stripe-mock (hoặc WireMock) thay Stripe, giới hạn CPU / RAM của `app`, `gateway` để 500 user đủ gây áp lực (bước này của hạng mục 1, làm trên cụm).

## Chạy

JMeter lưu cookie `XSRF-TOKEN` thành biến `COOKIE_XSRF-TOKEN` khi bật `CookieManager.save.cookies`.

```bash
# Flash sale: 500 user tranh 100 ghế trong 10 giây
jmeter -n -t ticket-hub.jmx -JCookieManager.save.cookies=true \
  -Jscenario=flash -Jhost=<gateway> -Jport=443 -Jprotocol=https -Jusers=500 -JrampUp=10 \
  -l results/flash.jtl -e -o results/flash-report

# Load: 500 user, xem + giữ ghế, tải đều
jmeter -n -t ticket-hub.jmx -JCookieManager.save.cookies=true -Jscenario=load -Jusers=500 -JrampUp=60 ...

# Soak: 200 user trong 1-2 giờ (3600-7200 giây)
jmeter -n -t ticket-hub.jmx -JCookieManager.save.cookies=true -Jscenario=soak -Jusers=200 -JuseScheduler=true -Jduration=3600 -Jloops=-1 ...

# Stress: tăng -Jusers theo bậc cho tới khi gãy, ghi thành phần gãy trước

# Phòng chờ (sự kiện high_demand): vào hàng, hỏi GET /queue/{eventId}/status mỗi giây tới khi ADMITTED, rồi giữ ghế
# Spike 10 → 500 user trong 5 giây:
jmeter -n -t ticket-hub.jmx -JCookieManager.save.cookies=true -Jscenario=queue -Jusers=500 -JrampUp=5 ...
# Flash sale qua phòng chờ (500 user / 100 ghế):
jmeter -n -t ticket-hub.jmx -JCookieManager.save.cookies=true -Jscenario=queue -Jusers=500 -JrampUp=10 ...
```

### Dữ liệu cho kịch bản `queue`

Dựng sự kiện với `-v high_demand=true` rồi ghi meta phòng chờ vào Redis bằng lệnh in ở đầu `scripts/seed-event.sql` (Queue đọc meta từ Redis trước, nên không cần sự kiện trong Catalog). `max_active` mặc định 200 và gọi 20 người / giây: 500 user mất khoảng 25 giây để được gọi hết, trừ khi đặt `TICKETHUB_QUEUE_MAX_ACTIVE`, `TICKETHUB_QUEUE_ADMIT_PER_SECOND` khác. Kịch bản không có SSE; `-JqueuePollMs` (mặc định 1000) chỉnh nhịp hỏi `status`. p99 tính trên các request REST ở trên (join, status, giữ ghế).

Kết quả (`.jtl` và báo cáo HTML) lưu ở `docs/test/load/`; số đo tổng hợp ghi vào `docs/test/load/sprint-5-load.md`.

## Kiểm sau Flash sale (bất biến BR-01)

```sql
-- đúng 100 vé, 0 ghế trùng
SELECT count(*) FROM ticket.tickets WHERE status <> 'REVOKED';
-- truy vấn ghế trùng §10 phải trả 0 dòng
SELECT seat_id, count(*) FROM ticket.tickets WHERE status <> 'REVOKED' GROUP BY seat_id HAVING count(*) > 1;
```

Kịch bản này dừng ở bước giữ ghế (201 thắng, 409 thua đều là kết quả đúng). Phần thanh toán (stripe-mock, webhook giả ký đúng secret test) thêm khi có stripe-mock trên staging.

## Hai lần đo cho hạng mục 2 và 5

Cùng build, bật / tắt lớp Redis bằng biến `TICKETHUB_BOOKING_SEATHOLD_ENABLED` của chart `app` (mặc định `false` = baseline chỉ Postgres). Đo lần 1 với `false`, lần 2 với `true`; so p95 `POST /bookings`, p95 API đọc, số lần deadlock / retry (log `Deadlock khi giữ ghế`), số lần Redis chặn sớm (metric `tickethub_booking_seat_hold_redis_rejected_total`).
