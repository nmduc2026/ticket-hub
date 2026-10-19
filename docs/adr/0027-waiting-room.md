# 0027. Phòng chờ ảo

**Trạng thái:** Accepted

## Bối cảnh
Sự kiện `high_demand` cần phòng chờ để điều tiết số người vào chọn ghế cùng lúc. Thiết kế ban đầu có các vấn đề:
- **Semaphore rò permit**: Redisson semaphore lấy permit khi gọi user vào, trả khi booking xong. User đóng tab hoặc pod chết thì permit không được trả; Queue không nghe event booking nên cũng không biết lúc trả. Vài trăm lần như vậy là hàng chờ đứng im.
- **Hai nguồn sự thật cho lượt vào**: vừa key Redis vừa JWT admission, phải đồng bộ thu hồi.
- `ZPOPMIN` lấy user ra khỏi hàng cả khi kết nối SSE đang đứt → mất chỗ. `ZADD` không có `NX` → vào lại thì bị ghi đè thứ tự.
- **Session hết hạn khi chờ**: request SSE không cập nhật `lastAccessedTime`; chờ 40 phút thì session / refresh token hết hạn, `POST /bookings` bị 401 đúng lúc được gọi vào.
- Thuần theo thời điểm vào thì ai F5 nhanh hơn được lợi.

## Các phương án
- **Điều tiết:** `RPermitExpirableSemaphore` (permit có lease; vẫn giữ chỗ cho người đã bỏ đi tới hết lease), hoặc đếm số người đang ở trong.
- **Lượt vào:** JWT admission (phải quản lý khoá ký, khó thu hồi), hoặc key Redis.

## Quyết định
- **Lượt vào là key Redis** `queue:{eventId}:admit:{userId}` (TTL 15 phút), không phát JWT. Booking kiểm `EXISTS` trước khi giữ ghế và xoá key khi tạo booking thành công: 1 lượt vào tạo được 1 booking.
- **Điều tiết theo số người đang ở trong** = lượt vào còn hạn + booking `PENDING_PAYMENT` của sự kiện; dưới ngưỡng `max_active` mới gọi thêm, tối đa N người/giây. Không có permit phải trả, nên hệ thống tự cân bằng.
- **Nguồn đếm booking đang chờ thanh toán**: Queue tự giữ ZSET `queue:{eventId}:pending` từ event của Booking (`BookingCreated` → thêm với score = hạn giữ ghế + ân hạn; `BookingConfirmed` / `Expired` / `Cancelled` → xoá), phần tử quá hạn tự bị bỏ khi đếm. Đã loại:
  - Mỗi giây gọi đồng bộ `BookingApi` để đếm: trái ADR 0018 (gọi đồng bộ chỉ khi người dùng đang chờ), và thành một API call mỗi giây cho mỗi sự kiện khi tách service.
  - Bộ đếm cộng / trừ theo event: mất hoặc trùng event là lệch, phải thêm job đối soát. ZSET có hạn thì tự lành, và `ZADD` / `ZREM` vốn idempotent.
  - Gộp phòng chờ vào module `booking`: đếm dễ, nhưng lệch ranh giới service (ADR 0015).
- Phòng chờ mở 15 phút trước giờ mở bán; người vào trong khoảng này nhận score ngẫu nhiên (bốc thăm), người đến sau xếp theo thời điểm vào. `ZADD NX`.
- Kết nối lại hoặc `GET /queue/{eventId}/status` → còn key lượt vào thì trả `ADMITTED` ngay.
- Trang phòng chờ gọi `GET /me` mỗi 5 phút để giữ session.

## Hệ quả
- (+) Không có tài nguyên bị rò; người bỏ đi tự hết hạn.
- (+) Công bằng cho người đến sớm.
- (−) Điều tiết phản ứng chậm hơn semaphore một nhịp scheduler (1 giây).

Liên quan: BR-04; `overview.md` §7.1, §8.1, §9, §12, §14.4, ADR 0011, 0015, 0018.
