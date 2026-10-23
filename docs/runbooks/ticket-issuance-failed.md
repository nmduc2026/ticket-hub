# Runbook: phát vé thất bại (alert `TicketIssuanceFailed`)

**Mức:** bất biến. Counter `tickethub_tickets_issuance_failed_total` tăng: phát vé vi phạm UNIQUE (ghế đã có vé chưa thu hồi) và booking tự chuyển `REFUND_REQUESTED` với hoàn 100% (BR-07, BR-10). Liên quan: ADR 0019, `overview.md` §7.3 dòng 7-8.

Lỗi hạ tầng (DB mất kết nối) không tính vào đây: event được gửi lại, không hoàn.

## Xác định

- Log `app`: `TicketIssuanceFailed` kèm `bookingId`, `seatId`.
- Có phải ghế trùng thật không: chạy truy vấn ở `duplicate-seat-tickets.md`. Nếu có dòng, đó là sự cố oversell.

## Xử lý

1. Xác nhận booking đã sang `REFUND_REQUESTED` rồi `REFUNDED` (Stripe hoàn 100% gồm phí). Nếu `REFUND_FAILED`, làm theo `refund-failed.md`.
2. Tìm nguyên nhân gốc: vì sao hai booking cùng giữ một ghế (lỗi nhả ghế sau hoàn, race ở lớp 1/2). Đây là lỗi thiết kế cần sửa, không chỉ hoàn tiền.
3. Báo khách (email hoàn tiền tự gửi); cân nhắc xin lỗi thủ công.
