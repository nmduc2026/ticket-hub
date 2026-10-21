# Runbook: Redis giữ ghế không dùng được (alert `SeatHoldUnavailable`)

**Mức:** critical. Gauge `tickethub_booking_seat_hold_available` = 0 quá 1 phút: lớp 1 giữ ghế (Redis) không dùng được nên `POST /bookings` trả `503 SEAT_HOLD_UNAVAILABLE` (fail-closed). Xem sự kiện, thanh toán và vé của đơn đã giữ vẫn chạy. Không có nguy cơ bán trùng: Postgres vẫn là nơi quyết định (BR-01). Liên quan: overview.md §10, ADR 0020.

## Xác định

- `GET /actuator/health` có thành phần `seatHold` ở trạng thái `DEGRADED` (HTTP 200).
- Redis: `redis-cli -a $REDIS_PASSWORD ping`, `INFO replication`, kiểm pod / Sentinel.
- Log app: dòng ERROR "Redis giữ ghế không dùng được" (lỗi đầu tiên), sau đó là các lần probe thất bại.

## Xử lý

1. Khôi phục Redis (pod bị kill, đầy bộ nhớ, mất mạng tới Redis, Sentinel chưa bầu master mới).
2. Không cần restart app: mỗi instance tự kiểm tra Redis vài giây một lần; khi Redis về, instance dựng lại các key `seat:*` từ các dòng `HELD` rồi mới nhận giữ ghế lại. Dòng log "Redis giữ ghế sẵn sàng: dựng lại key cho N ghế" xác nhận.
3. Nếu Redis không khôi phục kịp mà cần bán tiếp ngay: tắt lớp Redis (`tickethub.booking.seat-hold.enabled=false`, restart) để chỉ còn lớp Postgres. Đúng nhưng chậm hơn dưới tải; bật lại sau khi Redis khoẻ.
4. Xác nhận gauge về 1 và `POST /bookings` thành công trở lại.

_Bổ sung sau diễn tập C4 (Sprint 5, hạng mục 14): thời gian phục hồi thực đo, hiện tượng quan sát được._
