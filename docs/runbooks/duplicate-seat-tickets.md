# Runbook: ghế trùng vé (alert `DuplicateSeatTickets`)

**Mức:** bất biến số 1 (oversell). Không có error budget; xử lý ngay. Metric `tickethub_invariant_duplicate_seat_tickets` > 0. Liên quan: `overview.md` §10, §16.3; ADR 0021.

## Xác định

```sql
SELECT event_id, seat_id, array_agg(id) AS ticket_ids, array_agg(booking_id) AS booking_ids
FROM ticket.tickets
WHERE status <> 'REVOKED'
GROUP BY event_id, seat_id HAVING count(*) > 1;
```

Bình thường không bao giờ ra dòng nào (UNIQUE từng phần chặn ở lớp 2). Có dòng nghĩa là ràng buộc bị bỏ hoặc có migration/thao tác tay sai.

## Xử lý

1. Dừng bán sự kiện bị ảnh hưởng (Admin: tạm dừng bán) để không tạo thêm.
2. Xác định vé hợp lệ (booking tạo sớm hơn, đã `CONFIRMED`) và vé thừa. Vé thừa: thu hồi và hoàn 100% theo `refund-failed.md` / luồng `RefundRequested` lý do phát vé lỗi.
3. Kiểm tra ràng buộc còn tồn tại: `\d ticket.tickets` phải có UNIQUE từng phần trên `(event_id, seat_id) WHERE status <> 'REVOKED'`. Thiếu thì đây là sự cố nghiêm trọng, tạo lại bằng Flyway.
4. Gauge tự về 0 ở lần chạy job kế tiếp; xác nhận alert resolved.

## Sau sự cố

Postmortem ở `docs/postmortems/` (nguyên nhân, số ghế, khách bị ảnh hưởng, đã hoàn chưa).
