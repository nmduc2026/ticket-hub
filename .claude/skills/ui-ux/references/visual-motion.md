# Typography, Color, Icon & Motion (VIS, MOT)

## Typography

- **VIS-01** [High] Cỡ chữ và line-height:
  - Body trên mobile ≥ 16px.
  - Không dùng chữ body < 12px ở bất kỳ đâu.
  - Line-height của body 1.5–1.75.
- **VIS-02** [Medium] Thang cỡ chữ và độ đậm lấy từ token (ví dụ 12/14/16/18/24/32; heading 600–700, body 400, label 500). Heading phải khác body rõ rệt.
- **VIS-03** [Medium] Số trong bảng, giá tiền, bộ đếm thời gian dùng tabular figures (`font-variant-numeric: tabular-nums`), để layout không nhảy khi số thay đổi.
- **VIS-04** [Low] Ngắt dòng và letter-spacing:
  - Không thu hẹp letter-spacing của body text.
  - Heading ngắn có thể dùng `text-wrap: balance`.
  - Không chèn `&nbsp;` hay `<br>` cứng để ép xuống dòng.

## Màu & theme

- **VIS-05** [High] Component dùng semantic token (primary, surface, on-surface, error...), không hardcode mã hex.
- **VIS-06** [High] Dark mode:
  - Thiết kế cùng lúc với light mode.
  - Dùng tông giảm bão hoà, không đảo ngược màu.
  - Kiểm tra tương phản riêng cho từng theme: chữ, viền, divider, các trạng thái.
- **VIS-07** [Medium] Màu chức năng (error, success, warning, info) nhất quán trên toàn sản phẩm và luôn đi kèm icon hoặc chữ (xem A11Y-03).
- **VIS-08** [Medium] Scrim của modal/drawer đủ đậm để nội dung phía trước dễ đọc. Đo trên nền thật, không dùng một độ mờ cố định cho mọi nơi.
- **VIS-09** [Medium] Shadow/elevation và radius theo thang token, không dùng giá trị tuỳ ý.

## Icon & hình ảnh

- **VIS-10** [Medium] Icon:
  - Dùng một bộ icon duy nhất, cùng độ dày nét.
  - Không trộn kiểu filled và outline ở cùng một cấp.
  - Kích thước theo token.
  - Dùng SVG, không dùng emoji làm icon.
- **VIS-11** [Low] Logo và thương hiệu bên thứ ba dùng asset chính thức, giữ đúng tỉ lệ và khoảng trống quanh logo.
- **VIS-12** [Low] Prototype và dữ liệu mẫu dùng nội dung thực tế, với độ dài thật và ngôn ngữ thật của sản phẩm, không dùng lorem ipsum.

## Motion

Reduced motion: xem A11Y-20.

- **MOT-01** [High] Animation phải có ý nghĩa: thể hiện quan hệ nguyên nhân – kết quả hoặc quan hệ không gian.
  - Mỗi view tối đa 1–2 phần tử chính có animation.
  - Animation lặp vô hạn chỉ dùng cho loading.
- **MOT-02** [High] Chỉ animate `transform` và `opacity`. Không animate `width` / `height` / `top` / `left`, không gây layout shift.
- **MOT-03** [High] Animation không chặn input, có thể bị ngắt giữa chừng, và thao tác mới huỷ animation đang chạy. Trạng thái cuối được set trực tiếp, logic không phụ thuộc vào `animationend` / `transitionend`.
- **MOT-04** [Medium] Duration và easing lấy từ token dùng chung:
  - Animation thoát ngắn hơn animation vào (khoảng 60–70%).
  - Vào dùng giảm tốc (ease-out), ra dùng tăng tốc (ease-in).
  - Linear chỉ dùng cho tiến trình hoặc chuyển động xoay đều.
- **MOT-05** [Medium] Chuyển trạng thái và chuyển màn hình:
  - Expand/collapse, mở modal chuyển mượt, không giật cục.
  - Modal/sheet xuất hiện từ phía phần tử kích hoạt.
  - Hướng chuyển màn hình tiến/lùi nhất quán.
- **MOT-06** [Low] Thông số tham khảo:
  - Stagger danh sách khoảng 30–50ms mỗi phần tử.
  - Scale khi bấm trong khoảng 0.95–1.05.
  - Phần tử mờ dần không dừng ở opacity < 0.2: hoặc ẩn hẳn, hoặc giữ hiển thị.
