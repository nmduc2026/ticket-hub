# 0026. Hoàn tiền và thu hồi vé

**Trạng thái:** Accepted

## Bối cảnh
Thiết kế ban đầu: vé chỉ bị thu hồi sau khi tiền hoàn xong, idempotency key cố định theo booking. Các kịch bản lỗi:
- **Hoàn thất bại** (thẻ đã đóng…) → booking kẹt `REFUND_REQUESTED` vĩnh viễn, không alert, không có đường hoàn thủ công.
- **Vé còn hiệu lực trong lúc chờ hoàn**: khách vừa được (hoặc sắp được) hoàn tiền vừa vào cổng được.
- **Key cố định `refund-{bookingId}`**: Stripe lưu kết quả theo key, nên tạo lại refund sau lần thất bại chỉ nhận lại đúng kết quả cũ.
- **Thứ tự giữa các topic**: Kafka chỉ đảm bảo thứ tự trong một topic. `RefundCompleted` (topic payment) làm ghế về `AVAILABLE` và ghế được bán cho C; `BookingConfirmed` của C tới Ticket trước khi vé cũ của A bị thu hồi → vi phạm UNIQUE khi phát vé cho C.

## Các phương án
- **Thu hồi vé sau khi tiền hoàn xong:** khách giữ vé lâu nhất có thể, nhưng có các lỗ hổng trên.
- **Thu hồi vé ngay khi khách yêu cầu hoàn** (fail-closed, giống P6).

## Quyết định
- Hoàn **theo cả booking**. Vé `REVOKED` **ngay khi khách gửi yêu cầu**; yêu cầu hoàn không huỷ được.
- Mỗi lần thử hoàn là một bản ghi `refunds`, `id` sinh trước khi gọi Stripe, idempotency key `refund-{refundId}`.
- Hoàn thất bại → `REFUND_FAILED` + alert; Admin hoàn thủ công (tạo lần thử mới); vé vẫn `REVOKED`.
- Booking chỉ nhả ghế về `AVAILABLE` sau khi nhận `TicketRevoked` của mọi vé trong booking.
- Huỷ sự kiện (BR-08): hoàn 100% gồm cả phí, đi qua hàng đợi có giới hạn tốc độ (thấp hơn rate limit của Stripe và bulkhead), Admin xem tiến độ.

## Hệ quả
- (+) Không có khoảng thời gian vé còn hiệu lực trong khi tiền đang được hoàn.
- (+) Mọi lần hoàn thất bại đều thấy được và thử lại được.
- (−) Khách không rút lại được yêu cầu hoàn.
- (−) Ghế đã hoàn được bán lại chậm hơn một nhịp (chờ `TicketRevoked`).

Liên quan: BR-07, BR-08, BR-09; `overview.md` §6.3, §7.4, §7.7, §7.8, §16.3, ADR 0019, 0022, 0024.
