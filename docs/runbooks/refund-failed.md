# Runbook: hoàn tiền thất bại (alert `RefundFailedBookings`)

**Mức:** bất biến. Gauge `tickethub_invariant_refund_failed_bookings` > 0: có booking `REFUND_FAILED`, khách chưa nhận lại tiền. Vé đã `REVOKED`. Liên quan: BR-07, ADR 0026.

## Xác định

Admin: `GET /api/v1/admin/bookings?status=REFUND_FAILED` hoặc màn `/admin/orders`. Hoặc:

```sql
SELECT r.id, r.booking_id, r.status, r.failure_reason, r.created_at FROM payment.refunds r WHERE r.status = 'FAILED' ORDER BY r.created_at DESC;
```

## Xử lý

1. Đọc `failure_reason` (thẻ đóng, hết hạn hoàn, số dư Stripe không đủ…).
2. Nếu nguyên nhân tạm thời hoặc đã khắc phục: Admin `POST /api/v1/admin/bookings/{id}/refund` (`REFUND_FAILED` → `REFUND_REQUESTED`, tạo lần thử mới với key mới).
3. Nếu Stripe không thể hoàn về thẻ: xử lý ngoài hệ thống (chuyển khoản/ Stripe Dashboard), ghi lại; sau đó đối soát tay trạng thái booking.
4. Xác nhận gauge về 0.
