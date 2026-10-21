# Sơ đồ ghế và giữ ghế: phần prototype chưa thể hiện

Phạm vi Sprint 2. Prototype [SeatSelect](prototype/SeatSelect.dc.html) (#5), [Checkout](prototype/Checkout.dc.html) (#6, phần giữ ghế) và [SystemStates](prototype/SystemStates.dc.html) (#23) đã có bố cục, nhãn và các trạng thái lỗi chính; file này chỉ ghi những gì prototype không vẽ: luồng lỗi API, polling, bàn phím. Quy tắc: BR-02, BR-03, BR-12; ADR 0008.

## 1. Chỗ làm khác bản hoàn chỉnh của prototype

| Prototype có | Sprint 2 làm |
|---|---|
| "Lượt chọn còn 14:12" (phòng chờ) | Ẩn tới Sprint 7 |
| Chọn ghế realtime | Polling (mục 3) tới Sprint 7 |
| Checkout bước 1 "Thông tin nhận vé", bước 2 "Thanh toán", phí nền tảng | Sprint 3. Sprint 2: `/checkout/:bookingId` chỉ có phần giữ ghế: danh sách ghế, tổng, đồng hồ "Giữ ghế còn", nút "Huỷ và chọn lại ghế" |
| Chia sẻ, Liên hệ ban tổ chức | Không làm trong Sprint 2 |

Khu không bán vẽ màu xám theo `--seat-off`, không có nhãn hạng vé, không chọn được. Ghế `HELD` (người khác giữ) và `SOLD` đều hiện như "Đã bán": khách không cần phân biệt. `NOT_FOR_SALE` hiện như khu không bán.

## 2. Luồng lỗi API → giao diện

Lỗi chặn luồng hiện ngay trong trang (banner phía trên sơ đồ); toast chỉ cho thông báo phụ. Chuỗi nằm ở `lib/error-messages.ts`.

| Tình huống | Hiển thị | Hành vi |
|---|---|---|
| `409 SEAT_UNAVAILABLE` khi bấm Tiếp tục | "Ghế vừa có người giữ" + ghế nào không còn trống | Tải lại snapshot ghế; bỏ khỏi lựa chọn ghế không còn trống, giữ các ghế còn; không ghế nào được giữ (đúng backend: tất cả hoặc không). Nút Tiếp tục dùng lại được, **sinh key mới** vì đây là lần bấm mới |
| `422 MAX_SEATS_EXCEEDED` | "Tối đa 6 ghế mỗi lượt" | FE đã chặn ghế thứ 7 từ trước; mã này chỉ là lưới an toàn |
| `422 TICKET_LIMIT_EXCEEDED` | "Sự kiện này cho tối đa {n} vé mỗi người, tính cả các đơn trước" (`n` = `maxTicketsPerUser`) | Giữ nguyên lựa chọn để khách bỏ bớt |
| `409 PENDING_BOOKING_EXISTS` | Trạng thái #23 "Đã có đơn chờ thanh toán" | Backend chưa trả `bookingId` trong Problem Details và chưa có `GET /me/bookings` (Sprint 3), nên chưa có link tới đơn đang giữ; chỉ báo và để khách chờ hết hạn. Đề xuất đổi API: thêm `bookingId` vào Problem Details của lỗi này |
| `409 EVENT_NOT_ON_SALE` | "Sự kiện chưa mở bán hoặc đã dừng bán" | Tải lại chi tiết sự kiện; về trang chi tiết |
| `422 IDEMPOTENCY_KEY_REUSED`, `DUPLICATE_SEATS`, `EMAIL_REQUIRED` | Thông báo chung "Không giữ được ghế, thử lại" | Lỗi lập trình / tài khoản thiếu email; không gợi ý thử lại vô hạn |
| `401` | Trạng thái #23 "Cần đăng nhập" | Chuyển tới đăng nhập, xong quay lại trang chọn ghế (chọn lại ghế) |
| `404` sự kiện / booking | Trạng thái #23 404 | — |
| Mất mạng hoặc `5xx` khi `POST /bookings` | "Chưa gửi được, thử lại" | Nút Thử lại **dùng lại đúng key** của lần bấm đó (AC-7: cùng key → cùng kết quả, không tạo đơn thứ hai) |
| `GET /bookings/{id}` trả `EXPIRED` | Trạng thái #23 "Hết thời gian giữ ghế" | Nút về trang chọn ghế của sự kiện |
| `GET /bookings/{id}` trả `CANCELLED` | Như hết giờ, nội dung "Đơn đã huỷ" | — |
| `DELETE /bookings/{id}` → `204` | Toast "Đã huỷ giữ ghế" | Về trang chọn ghế, tải lại snapshot (ghế đã trống) |
| `DELETE` → `409 BOOKING_NOT_CANCELLABLE` | "Đơn đã hết hạn hoặc không còn chờ thanh toán" | Tải lại đơn, hiện đúng trạng thái |

Đồng hồ đếm ngược tính từ `expiresAt` của server (BR-03). Hết giờ ở client thì **hỏi lại server** (`GET /bookings/{id}`) rồi mới chuyển màn, không tự kết luận. Chặn thanh toán còn dưới 90 giây áp dụng từ Sprint 3.

## 3. Polling trạng thái ghế

- `GET /events/{id}/seats` mỗi 5 giây khi tab đang hiển thị; tạm dừng khi tab ẩn; tải lại ngay khi tab hiện lại hoặc mạng có lại.
- Cập nhật theo `seatId`: chỉ ghế đổi trạng thái được vẽ lại (component ghế `memo`), không dựng lại cả sơ đồ.
- Ghế khách **đang chọn** mà bị người khác giữ: bỏ khỏi lựa chọn, báo trên vùng `aria-live` "Ghế B7 vừa có người giữ".
- Mất kết nối: banner "Mất kết nối mạng · đang kết nối lại. Trạng thái ghế có thể chưa mới nhất." kèm "Thử lại ngay" (có trong #23); sơ đồ vẫn xem được, nút Tiếp tục khoá tới khi tải lại thành công.
- Nguồn dữ liệu nằm sau một hook (`useSeatSnapshot`), để Sprint 7 thay polling bằng SSE (`lib/sse.ts`) mà không đổi component.

## 4. Bàn phím và trình đọc màn hình trên canvas

Canvas không có DOM cho từng ghế (ADR 0008), nên thêm một lớp truy cập song song:

- Vùng sơ đồ là một phần tử focus được (`role="application"`, nhãn "Sơ đồ ghế"); focus hiện bằng viền ghế vẽ trên canvas.
- Mũi tên: sang ghế liền kề cùng hàng (trái / phải) hoặc ghế gần nhất ở hàng trên / dưới. `Home` / `End`: đầu / cuối hàng. `PageUp` / `PageDown`: khu trước / sau.
- `Enter` hoặc `Space`: chọn / bỏ chọn. Ghế không chọn được vẫn focus được; `Enter` đọc lý do (đã bán, khu không bán, đã đủ 6 ghế).
- `+` / `-` zoom, `0` về khung nhìn ban đầu; di chuyển focus tự cuộn ghế vào khung nhìn.
- Mỗi lần focus đổi, vùng `aria-live="polite"` đọc: "Hàng B, ghế 7, hạng VIP, 2.500.000 ₫, còn trống" (thêm "đã chọn" khi đang chọn).
- Danh sách "Ghế đã chọn" bên cạnh là DOM thường, mỗi dòng có nút "Bỏ chọn {ghế}", nên khách dùng trình đọc màn hình vẫn bỏ chọn được mà không cần canvas.
- Tôn trọng `prefers-reduced-motion`: zoom / pan không có chuyển động mượt.
