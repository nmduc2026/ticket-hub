---
name: ticket-hub-coding-conventions
description: "Quy tắc code chung của Ticket Hub cho cả backend và frontend: code rõ ràng, tái sử dụng đúng chỗ, đặt tên theo ngôn ngữ nghiệp vụ, ranh giới module, tiền và thời gian, xử lý lỗi, bảo mật, cấu hình. Dùng khi viết, sửa hoặc review bất kỳ code nào trong repo Ticket Hub, trước khi dùng skill riêng cho Spring Boot hay React."
---

# Ticket Hub: quy tắc code chung

Áp dụng cho mọi code. Quy tắc riêng theo stack: `ticket-hub-springbot-conventions` (backend), `ticket-hub-react-conventions` (frontend), `ticket-hub-testcase-rule` (test).

## 1. Nguyên tắc

- **Rõ ràng hơn khéo léo.** Người đọc code là PM đang học; code phải đọc được từ trên xuống mà không cần đoán.
- **Làm đúng yêu cầu, không làm thêm.** Không thêm tham số, cấu hình hay lớp trừu tượng "để sau này dùng".
- **Một hàm một việc**, tên nói đúng việc đó. Hàm dài quá ~40 dòng hoặc lồng quá 3 cấp thì tách.
- **Không lặp logic nghiệp vụ.** Quy tắc BR-xx chỉ được cài đặt ở **một** chỗ; nơi khác gọi lại.
- **Tái sử dụng có điều kiện:** chỉ tách thành hàm / component dùng chung khi đã có **từ 2–3 chỗ dùng thật** và chúng giống nhau về nghĩa, không chỉ giống về hình thức.
- **Chỗ để code dùng chung:** backend `com.ndz.backend.shared`; frontend `src/lib/` (hàm), `src/components/` (component không thuộc feature nào). Code chỉ một feature dùng thì để trong feature đó.
- **Bất biến trước, tối ưu sau:** không bán trùng ghế, không mất tiền (`overview.md` §1.1) quan trọng hơn mọi tối ưu hiệu năng.

## 2. Đặt tên

- Code, tên bảng, API, commit: **tiếng Anh**. Comment và tài liệu: tiếng Việt được.
- Dùng đúng thuật ngữ nghiệp vụ dưới đây, không tự đặt từ đồng nghĩa:

| Nghiệp vụ | Trong code |
|---|---|
| Sự kiện / buổi diễn | `Event` (khác với domain event: dùng hậu tố `...Event` cho record event, vd. `BookingConfirmed`) |
| Ban tổ chức | `Organizer` |
| Địa điểm, sơ đồ ghế, khu, ghế | `Venue`, `SeatMap`, `Section`, `Seat` |
| Hạng vé | `TicketTier` |
| Tồn kho ghế theo sự kiện | `SeatInventory` |
| Đơn / lượt giữ ghế | `Booking`, `BookingItem` |
| Lượt vào phòng chờ | `Admission` |
| Phí nền tảng | `platformFee`, `applicationFee` (khi nói về Stripe) |
| Hoàn tiền / yêu cầu hoàn | `Refund`, `RefundRequest` |

- Boolean bắt đầu bằng `is` / `has` / `can`. Hằng số `UPPER_SNAKE_CASE`. Mã lỗi nghiệp vụ `UPPER_SNAKE_CASE` (vd. `SEAT_UNAVAILABLE`, `HOLD_EXPIRING`).
- Tên phản ánh đơn vị: `amountMinor`, `holdDuration`, `expiresAt`, `feeBps`.

## 3. Ranh giới module

- Module: `account`, `catalog`, `booking`, `payment`, `ticket`, `notification`, `shared`, `queue` (khi có).
- **Không import class nội bộ của module khác.** Chỉ dùng interface công khai của module hoặc nghe event của nó (`overview.md` §4.2 P1, ADR 0012, 0018).
- Không đọc bảng của module khác, kể cả bằng SQL thuần hay jOOQ.
- Frontend: một feature không import file nội bộ của feature khác; dùng chung thì chuyển lên `src/lib/` hoặc `src/components/`.

## 4. Tiền và thời gian

- Tiền là **số nguyên đơn vị nhỏ nhất** (`long amountMinor`) + mã tiền tệ. VND là zero-decimal: 50.000đ là `50000`. **Không dùng `double` / `float` / `BigDecimal` chia có phần lẻ** cho tiền (BR-10).
- Phí nền tảng: `floor(totalMinor * feeBps / 10000)`, làm tròn xuống, tính ở một hàm duy nhất.
- Thời gian lưu và truyền dưới dạng **UTC** (`Instant`, ISO-8601 có `Z`); chỉ đổi sang `Asia/Ho_Chi_Minh` ở lớp hiển thị (BR-11).
- Code phụ thuộc thời gian nhận `Clock` (backend) hoặc hàm `now()` truyền vào được, để test không phải chờ.

## 5. Cấu hình, không hard-code

Mọi con số nghiệp vụ đọc từ cấu hình, có giá trị mặc định theo BR: thời gian giữ ghế 10 phút, chặn thanh toán khi còn 90 giây, ân hạn 5 phút, tối đa 6 ghế, phí 5%, lượt vào 15 phút, giữ idempotency key 24 giờ. Không rải số này trong code.

## 6. Lỗi

- **Không nuốt lỗi.** Không có `catch` rỗng; không `catch` rồi chỉ log rồi chạy tiếp như chưa có gì.
- Lỗi nghiệp vụ có mã (`SEAT_UNAVAILABLE`…) và trả về theo RFC 9457 Problem Details. Lỗi hạ tầng để framework xử lý (retry, DLT, 5xx).
- Phân biệt rõ: lỗi nghiệp vụ → bù trừ / báo người dùng; lỗi tạm thời → retry có backoff (`overview.md` §7.3).

## 7. Đồng thời và idempotency

- Không "kiểm tra rồi mới làm" ngoài transaction / khoá (check-then-act). Dùng điều kiện trong câu lệnh (`UPDATE ... WHERE status = 'AVAILABLE'`) hoặc khoá.
- Mọi API ghi có `Idempotency-Key`, mọi consumer, mọi lệnh gọi Stripe đều chịu được việc bị gọi lặp (`overview.md` §4.2 P4, §6.7).
- Chuyển trạng thái chỉ theo máy trạng thái ở `overview.md` §7.7, cài đặt tập trung, không `setStatus` rải rác.

## 8. Bảo mật

- Không log, không trả về, không commit: token, secret, số thẻ, `client_secret` của Stripe. Email chỉ log khi thật cần, ưu tiên `userId`.
- Không tin ID từ client: luôn kiểm quyền sở hữu ở tầng service (chống IDOR).
- Input luôn được validate; SQL luôn tham số hoá.

## 9. Comment

- Comment giải thích **tại sao**, không lặp lại code làm gì.
- Code cài đặt quy tắc nghiệp vụ ghi mã BR (vd. `// BR-03: ân hạn 5 phút khi đã bắt đầu thanh toán`).
- Không để code chết, code comment-out. `TODO` phải kèm lý do và việc cần làm.

## 10. Checklist trước khi bàn giao

- [ ] Đúng phạm vi task, không làm thêm.
- [ ] Không vi phạm ranh giới module.
- [ ] Tiền là `long amountMinor`, thời gian UTC, số nghiệp vụ lấy từ cấu hình.
- [ ] Không nuốt lỗi; lỗi nghiệp vụ có mã.
- [ ] Không secret / PII trong log hay code.
- [ ] Có test theo `ticket-hub-testcase-rule`; lint / format / CI xanh.
