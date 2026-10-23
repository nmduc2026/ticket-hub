# Runbook: payment đã thu tiền mà booking chưa xong (alert `PaymentSucceededUnsettled`)

**Mức:** bất biến. Khách đã bị trừ tiền thì phải có vé hoặc được hoàn (BR-10). Metric `tickethub_payment_succeeded_unsettled` > 0 quá 5 phút. Liên quan: ADR 0019, `overview.md` §7.3.

## Xác định

```sql
SELECT p.id, p.booking_id, p.status, p.created_at, b.status AS booking_status
FROM payment.payments p JOIN booking.bookings b ON b.id = p.booking_id
WHERE p.status = 'SUCCEEDED' AND b.status NOT IN ('CONFIRMED','REFUND_REQUESTED','REFUNDED');
```

## Nguyên nhân thường gặp

- Consumer `PaymentSucceeded` ở Booking lỗi hạ tầng và đang chờ job `RepublishIncompleteEvents` (30 giây một lần): thường tự hết.
- Booking đã `EXPIRED` trước khi tiền về: phải tự chuyển hoàn (BR-10). Kiểm tra log `BookingConfirmationFailed`.
- Webhook Stripe không tới: job đối soát (`PaymentReconciliationService`) tự hỏi Stripe; xem log của job.

## Xử lý

1. Xem log `app` theo `bookingId` (tìm bằng `traceId` trong Jaeger/Kibana nếu có).
2. Nếu event treo: chờ một chu kỳ republish; nếu vẫn treo, xem exception và sửa nguyên nhân.
3. Nếu booking hết hạn mà chưa hoàn: Admin `POST /admin/bookings/{id}/refund` (chỉ áp dụng cho `REFUND_FAILED`) hoặc hoàn thủ công trên Stripe Dashboard, ghi lại lý do.
4. Xác nhận gauge về 0.
