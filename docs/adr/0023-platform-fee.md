# 0023. Phí nền tảng chốt vào sự kiện lúc publish

**Trạng thái:** Accepted

## Bối cảnh
Phí nền tảng mặc định 5%, Admin cấu hình được (BR-06). Câu hỏi: Admin đổi phí 5% → 3% giữa đợt bán thì booking nào áp phí nào, và Payment lấy phần trăm ở đâu khi tạo PaymentIntent?

## Các phương án
- **Áp theo thời điểm đặt vé:** hai khách cùng sự kiện, cùng hạng ghế có thể chịu phí khác nhau; organizer không biết trước phí khi định giá.
- **Chốt vào sự kiện lúc publish.**

## Quyết định
**Chốt lúc publish.**
- Lịch sử mức phí lưu ở `platform_fee_policies`.
- Publish ghi `events.platform_fee_bps`; giá trị này đi theo event sang `event_snapshots` của Booking.
- Khi tạo PaymentIntent, Booking trả `platform_fee_bps`, Payment tính `application_fee_minor = floor(total_minor × platform_fee_bps / 10000)`.
- Đổi mức phí chỉ áp cho sự kiện publish sau đó.

## Hệ quả
- (+) Organizer biết phí trước khi định giá; mọi vé của một sự kiện cùng mức phí.
- (+) Không phải lưu phí riêng theo từng booking.
- (−) Đổi phí không áp cho sự kiện đang bán.

Liên quan: BR-06, BR-10, BR-14; `overview.md` §6.1, §6.2, §11.2, ADR 0022.
