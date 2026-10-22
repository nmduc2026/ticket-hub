# Check-in tại cổng (màn #10)

Bổ sung cho prototype [Checkin](prototype/Checkin.dc.html): phần prototype chưa thể hiện (quyền camera, âm thanh, mất mạng, lỗi từ server). Căn cứ: `overview.md` §7.5, §14.4 (Check-in), BR-09, ADR 0024.

## 1. Phạm vi

- Route `/checkin/:eventId`, dành cho organizer sở hữu sự kiện. Tối ưu cho điện thoại dọc (390px), toàn màn hình, không dùng khung Admin / Organizer.
- Người soát vé dùng điện thoại của mình; cổng soát vé (vd. "Cổng 2") nhập một lần, nhớ trên máy (chỉ tên cổng, không dữ liệu nhạy cảm).
- PWA ở mức cài được lên màn hình chính (`manifest`, chế độ standalone). **Không có service worker và không check-in offline** (ADR 0024): mất mạng thì không cho vào.
- Số đếm "Đã vào 312 / 1.200 vé" hiện dưới tên sự kiện ở thanh trên, lấy từ `GET /organizer/events/{id}/sales` (`checkedIn` / `ticketsSold`), làm mới sau mỗi vé hợp lệ và mỗi 30 giây để thấy cả các cổng khác. Lỗi hay đang tải thì ẩn, không ảnh hưởng việc quét. Dòng "Người mua": API check-in chưa trả, **ẩn** cho tới khi có API.

## 2. Luồng

1. **Bắt đầu:** ô "Cổng soát vé" (bắt buộc, tối đa 50 ký tự), nút "Bắt đầu quét". Bấm nút này là thao tác của người dùng nên cũng mở khoá âm thanh của trình duyệt.
2. **Quét:** camera sau (`facingMode: environment`), khung ngắm, chỉ đọc mã QR. Đang xử lý một mã thì tạm dừng camera, không đọc mã khác.
3. **Xác minh chữ ký trên máy** bằng khoá công khai của `GET /checkin/keys` (thư viện `jose`, chọn khoá theo `kid`). Sai chữ ký, `kid` lạ hoặc không phải JWT → **vé giả, không gọi server**. `eventId` trong QR khác sự kiện đang soát → "Vé của sự kiện khác", không gọi server.
4. **Xác nhận với server:** `POST /checkins {qrToken, gate}`. Chỉ khi server trả `200` mới được vào.
5. **Kết quả** chiếm cả màn hình (mục 3), nút "Quét vé tiếp theo" quay lại bước 2.

## 3. Kết quả

Mỗi kết quả có **màu + biểu tượng + tiêu đề chữ lớn** (không chỉ dựa vào màu), một dòng giải thích và tối đa 2 dòng thông tin. Vùng kết quả là `role="alert"` nên trình đọc màn hình đọc ngay.

| Tình huống | Tiêu đề | Màu | Âm thanh | Gọi server |
|---|---|---|---|---|
| `200` | Hợp lệ | Xanh | 1 tiếng cao ngắn | Có |
| `409 TICKET_ALREADY_USED` | Vé đã được sử dụng | Vàng | 2 tiếng thấp | Có |
| `409 TICKET_REVOKED` | Vé đã bị thu hồi | Đỏ | 2 tiếng thấp | Có |
| Sai chữ ký / `kid` lạ / không phải JWT | Vé không hợp lệ | Đỏ | 2 tiếng thấp | Không |
| `eventId` khác sự kiện đang soát | Vé của sự kiện khác | Đỏ | 2 tiếng thấp | Không |
| `404 TICKET_NOT_FOUND` | Không tìm thấy vé | Đỏ | 2 tiếng thấp | Có |
| `403 NOT_EVENT_OWNER` | Không có quyền soát sự kiện này | Đỏ | 2 tiếng thấp | Có |
| `422 CHECKIN_NOT_OPEN` | Chưa mở check-in | Vàng | 2 tiếng thấp | Có |
| `422 EVENT_ENDED` | Sự kiện đã kết thúc | Vàng | 2 tiếng thấp | Có |
| Mất mạng / server không trả lời | Mất kết nối | Đỏ | 2 tiếng thấp | Không tới được |
| Lỗi khác (5xx...) | Chưa check-in được | Đỏ | 2 tiếng thấp | Có |

- **Hợp lệ:** dòng "Vị trí" lấy từ `seat` của response (vd. `A-B-7`), dòng "Mã vé" là 8 ký tự cuối của `ticketId`.
- **Mất mạng không cho vào:** chữ ký đúng không chứng minh vé chưa dùng hay chưa thu hồi, nên không có chế độ "cho vào tạm". Nút "Quét lại" thử lại cùng mã.
- Khi trình duyệt báo offline, hiện dải cảnh báo "Mất kết nối. Chưa check-in được." cố định trên đầu và tạm dừng camera tới khi có mạng lại.

## 4. Camera

| Trạng thái | Hiển thị |
|---|---|
| Đang xin quyền | Camera hiện dần, chữ "Đang bật camera…" |
| Từ chối quyền (`permission-denied`) | "Chưa được dùng camera" + nút "Thử lại"; mô tả cách bật quyền trong cài đặt trình duyệt |
| Không có camera / camera đang bị app khác dùng | "Không tìm thấy camera" / "Camera đang được dùng ở ứng dụng khác" + "Thử lại" |
| Không phải HTTPS (`insecure-context`) / trình duyệt không hỗ trợ | "Trình duyệt này không quét được. Mở trang bằng Chrome hoặc Safari qua HTTPS." |

## 5. Âm thanh và rung

- Âm tạo bằng Web Audio (không tải file). Nút loa ở góc trên bật / tắt tiếng, mặc định bật; trạng thái nhớ trên máy.
- Rung ngắn (`navigator.vibrate`) khi hợp lệ, rung dài hơn khi bị từ chối, nếu thiết bị hỗ trợ.

## 6. Trợ năng

- Nút quay lại, loa, "Quét vé tiếp theo" cao tối thiểu 44px.
- Tiêu đề kết quả là `h1` của trạng thái; focus chuyển vào nút "Quét vé tiếp theo" khi hiện kết quả.
- Tôn trọng `prefers-reduced-motion`: không hoạt ảnh quét khi bật.
