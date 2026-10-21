# Thanh toán, vé, đơn: phần prototype chưa thể hiện

Áp dụng cho màn #6 Thanh toán, #7 Thành công, #8 Vé của tôi, #9 Chi tiết vé, #21 Đơn hàng của tôi. Bố cục, chữ, token theo prototype; file này chỉ ghi chỗ prototype để trống. Chỗ ghi "Cần duyệt" chờ người dùng xác nhận.

## 1. Thanh toán (#6)

Prototype vẽ form thẻ tự dựng. Bản thật dùng Stripe Payment Element đặt vào đúng khung "2 Thanh toán"; các tab Thẻ / Google Pay / Apple Pay do Payment Element tự hiển thị theo thiết bị. Khối "1 Thông tin nhận vé" chỉ hiện dòng email của tài khoản (chỉ đọc, từ `GET /me`); họ tên và số điện thoại không thu vì API thanh toán không nhận. Cần duyệt.

| Trạng thái | Hành vi |
|---|---|
| Vào màn | Gọi `POST /payments` một lần (Idempotency-Key sinh khi vào màn, giữ khi thử lại). Trong lúc chờ: skeleton ở khung Payment Element, nút Thanh toán khoá |
| Còn < 90 giây | Nút Thanh toán khoá, dòng cảnh báo dưới đồng hồ: "Còn quá ít thời gian để thanh toán an toàn. Hãy chọn lại ghế." kèm nút "Chọn lại ghế". Server trả `409 HOLD_EXPIRING` cũng hiện đúng khối này |
| Đang xác nhận | Nút hiện spinner "Đang xử lý", khoá cả form và nút "Huỷ và chọn lại ghế"; không cho bấm hai lần |
| Thẻ bị từ chối | Lỗi hiện ngay dưới Payment Element (`role="alert"`): "Thẻ bị từ chối. Thử thẻ khác hoặc phương thức khác." Form mở lại, vẫn cùng một `clientSecret` và đồng hồ chạy tiếp. Không tạo payment mới |
| Lỗi xác thực 3DS không hoàn tất | Như thẻ bị từ chối, nội dung: "Chưa xác thực được thẻ. Thử lại." |
| Lỗi mạng / Stripe không tải | Khối lỗi có nút "Thử lại" (tải lại Payment Element); đơn và đồng hồ không đổi |
| `BOOKING_NOT_PAYABLE` | Chuyển sang trạng thái đơn theo `GET /bookings/{id}` (hết giờ, đã huỷ, đã xác nhận) |
| Hết giờ giữ ghế giữa lúc trả | Xem mục 2 |

Tổng tiền: dòng "Trong đó phí nền tảng … không hoàn lại" lấy từ `platformFeeMinor` của `POST /payments`, tính bằng tiền, không tự tính ở client.

## 2. Chờ xác nhận và hết giờ giữ ghế giữa lúc trả

Sau khi Stripe báo thành công ở trình duyệt, tiền đã trừ nhưng đơn chỉ `CONFIRMED` khi webhook về. Hiển thị:

1. Thay form bằng khối "Đang xác nhận thanh toán" (spinner, đồng hồ giữ ghế ẩn). Hỏi lại `GET /bookings/{id}` mỗi 2 giây.
2. `CONFIRMED` → chuyển sang #7.
3. Quá 30 giây vẫn chưa `CONFIRMED`: giữ khối chờ, thêm dòng "Thanh toán đã được ghi nhận. Vé sẽ gửi qua email khi xác nhận xong." và nút "Xem đơn của tôi" (#21). Tiếp tục hỏi lại mỗi 5 giây tới tối đa 5 phút; sau đó dừng, chỉ còn nút.
4. Đơn `EXPIRED` / `CANCELLED` sau khi đã trả (thanh toán đến muộn): vẫn giữ khối chờ, vì server đang giữ lại ghế hoặc hoàn tiền. Chỉ khi ghế mất, đơn giữ trạng thái cũ: hiện "Không giữ lại được ghế. Tiền sẽ được hoàn tự động về thẻ." và nút "Chọn lại ghế".

Đồng hồ về 0 khi chưa bấm Thanh toán: khối "Hết thời gian giữ ghế" như hiện có. Đồng hồ về 0 trong lúc đang xác nhận với Stripe: không đổi màn, đợi kết quả; nếu thẻ bị từ chối thì rơi vào khối hết giờ.

## 3. Thành công (#7)

Vào bằng điều hướng sau `CONFIRMED`, hoặc mở lại link: nếu đơn không phải `CONFIRMED` thì chuyển về #6. Danh sách vé lấy từ `GET /me/tickets`; chưa có vé (phát vé chậm hơn xác nhận vài giây) thì hiện skeleton và hỏi lại tới khi có. "Tải PDF" và "Thêm vào lịch" chưa làm (ẩn) cho tới khi có API.

## 4. Vé của tôi (#8) và chi tiết vé (#9)

- Tab "Sắp diễn ra / Đã qua / Đã hoàn": chia theo `startsAt` và `status` của vé ở client trên trang đã tải. Cần duyệt: API hiện chỉ có `page`, `size`; tab "Đã hoàn" trống đến Sprint 4.
- QR vẽ bằng `qrcode.react` từ `qrToken`, mức sửa lỗi M, nền trắng viền trắng 8px để quét được cả ở chế độ tối.
- Vé không `VALID` (đã dùng, hoàn): QR mờ kèm nhãn trạng thái, không hiển thị mã quét.
- "Yêu cầu hoàn đơn" và hạn hoàn: ẩn đến Sprint 4.

## 5. Đơn hàng của tôi (#21)

- Cột và trạng thái theo prototype; nút "Tiếp tục thanh toán" chỉ cho đơn `PENDING_PAYMENT` (về #6), "Xem vé" cho đơn `CONFIRMED`.
- "Tải hoá đơn", "Yêu cầu hoàn vé", chi tiết đơn nhiều dòng: ẩn đến khi có API (Sprint 4).
- Tìm kiếm, lọc trạng thái, số dòng mỗi trang: danh sách phân trang theo server (`page`, `size`), chưa lọc theo server nên ô tìm và lọc trạng thái chỉ làm trên trang đang xem. Cần duyệt.

## 6. Văn bản lỗi mới

| Mã | Chữ |
|---|---|
| `HOLD_EXPIRING` | Còn quá ít thời gian để thanh toán. Chọn lại ghế. |
| `BOOKING_NOT_PAYABLE` | Đơn không còn chờ thanh toán. |
