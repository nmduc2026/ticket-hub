# 0020. Giữ ghế và hết hạn

**Trạng thái:** Accepted

## Bối cảnh
Giữ ghế ghi vào hai nơi: Redis (Lua, lọc tranh chấp nhanh) và Postgres (nguồn sự thật). Các kịch bản lỗi:
- **Ghế ma.** Lua giữ ghế xong mà TX Postgres rollback (lệch lớp 2, vi phạm `uq_one_pending_per_user`, DB lỗi), hoặc pod chết giữa chừng → key Redis sống 10 phút dù DB ghi `AVAILABLE`. Trường hợp dễ gặp nhất: user đã có booking chờ thanh toán mở tab khác chọn 6 ghế → Lua thành công, DB từ chối, 6 ghế bị khoá 10 phút.
- **TTL Redis hết trước job.** TTL hết lúc T, `ExpireBookings` chạy ở T+0..30s. Trong khoảng đó user B chạy Lua thành công, lớp 2 từ chối (DB vẫn `HELD` cho A), key giờ mang `bookingId` của B thêm 10 phút → ghế trống thật mà không bán được, đúng lúc flash sale.
- **Hết hạn khi đang xác thực 3DS.** Khách bấm trả ở phút 9:30 → lỗi giữa chừng, hoặc trả xong nhưng ghế đã bán → hoàn tự động.
- **Deadlock.** Hai booking có tập ghế chồng nhau khoá dòng theo thứ tự khác nhau → `40P01`. Xảy ra khi Redis không lọc được (failover Sentinel mất ghi, hoặc giai đoạn chỉ có Postgres).

## Các phương án
- **Redis quyết định hết hạn bằng TTL:** đơn giản, nhưng gây ghế ma và lệch với Postgres như trên.
- **Postgres là nguồn quyết định duy nhất, Redis chỉ là lớp lọc.**

## Quyết định
**Postgres quyết định.**
- Kiểm quy tắc (sự kiện `ON_SALE`, lượt vào, BR-02) **trước** Lua; vi phạm thì không tạo key nào.
- TX thất bại vì bất kỳ lý do gì → Lua compare-and-delete xoá key mang đúng `bookingId` đó.
- Hạn tính theo `expires_at` (cộng ân hạn nếu đã có `payment_started_at`). TTL Redis = hạn + 30 giây, luôn dài hơn; `ExpireBookings` xoá key chủ động; TTL chỉ là lưới an toàn.
- Chặn bắt đầu thanh toán khi còn dưới 90 giây; booking đã bắt đầu thanh toán được ân hạn 5 phút. Booking không biết trạng thái payment, nên Payment gọi đồng bộ "bắt đầu thanh toán" để Booking ghi `payment_started_at`.
- Khoá dòng theo thứ tự `seat_id` (`SELECT … ORDER BY seat_id FOR UPDATE`); deadlock thì thử lại 1 lần rồi trả `409 SEAT_UNAVAILABLE`.
- Redis chết → dừng giữ ghế (fail-closed); phục hồi thì dựng lại key từ các dòng `HELD` rồi mới mở lại.
- Lớp Redis thêm sau (Sprint 5), đo hiệu năng trước và sau.

## Hệ quả
- (+) Không oversell kể cả khi Redis mất dữ liệu; không còn ghế ma.
- (+) Đo được lớp Redis thực sự cải thiện hiệu năng bao nhiêu.
- (−) Mỗi lần giữ ghế ghi hai nơi; phải có test tích hợp cho từng nhánh rollback.

Liên quan: BR-01, BR-02, BR-03; `overview.md` §7.2, §7.3, §9, §10, ADR 0019.
