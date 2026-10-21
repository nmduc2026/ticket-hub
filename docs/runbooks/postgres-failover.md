# Runbook: Postgres primary chết, failover (C1)

**Mục tiêu:** failover < 30 giây, tạo booking mới vẫn được, không mất booking đã commit. Cụm `tickethub-pg` (CloudNativePG) có 1 primary + 2 replica; Service `tickethub-pg-rw` luôn trỏ primary hiện tại.

Mọi lệnh `kubectl` lên cụm cần báo trước (`CLAUDE.md`).

## Phát hiện

Alert `PostgresPrimaryDown` hoặc app báo lỗi kết nối. Xem trạng thái:

```bash
kubectl cnpg status tickethub-pg -n tickethub
kubectl -n tickethub get pods -l cnpg.io/cluster=tickethub-pg -L role
```

## Tự động

Operator thăng một replica có WAL mới nhất lên primary và đổi `tickethub-pg-rw`. HikariCP của app tự kết nối lại; request đang chạy lỗi một lần rồi thử lại được. Booking đã commit không mất vì replica nhận WAL đồng bộ mức streaming (có thể mất giao dịch chưa kịp stream trong khoảnh khắc chết, không phải booking đã trả `201`).

## Diễn tập (C1)

1. Chạy tải nhẹ: tạo booking liên tục (`load-tests/jmeter` hoặc vòng lặp `curl`) và ghi thời điểm.
2. Giết primary: `kubectl -n tickethub delete pod $(kubectl -n tickethub get pod -l cnpg.io/cluster=tickethub-pg,role=primary -o name)`.
3. Đo: thời gian từ lúc xoá đến khi `kubectl cnpg status` báo primary mới `Healthy`, và thời gian từ lúc đó đến khi `POST /bookings` thành công trở lại. Cả hai phải cộng lại < 30 giây.
4. Kiểm không mất dữ liệu: đối chiếu số booking `201` ở phía tải với `select count(*) from booking.bookings`.
5. Pod cũ tự quay về làm replica (`pg_rewind`); xác nhận 3 instance `Ready`.

## Khi không tự phục hồi

- `kubectl cnpg status` báo thiếu replica đồng bộ hoặc replication lag lớn: xem log `kubectl logs <pod> -c postgres`.
- Thăng thủ công: `kubectl cnpg promote tickethub-pg <tên-pod-replica> -n tickethub`.
- Cả 3 instance hỏng: khôi phục từ backup theo `postgres-pitr.md`.

## Sau sự cố

Ghi postmortem ở `docs/postmortems/` kèm thời gian failover đo được.
