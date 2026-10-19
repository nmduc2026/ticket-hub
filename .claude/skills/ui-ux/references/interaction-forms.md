# Interaction, Feedback & Forms (INT, FORM)

## Trạng thái & phản hồi

- **INT-01** [Critical] Mỗi màn hình/component hiển thị dữ liệu được thiết kế và code đủ trạng thái:
  - loading, empty, error, success;
  - dữ liệu một phần (partial);
  - không có quyền.
- **INT-02** [High] Control có trạng thái phân biệt rõ: hover (web), pressed/active, focus, disabled.
  - Phản hồi khi bấm trong khoảng 100ms.
  - Trạng thái pressed không làm xê dịch layout.
- **INT-03** [High] Nút gửi thao tác async: disable và hiện loading trong lúc xử lý, để chống gửi trùng.
- **INT-04** [High] Loading khớp với thời gian chờ:
  - Thao tác gần như tức thì: không chớp spinner.
  - Chờ trên khoảng 1s: dùng skeleton cho nội dung.
  - Chờ lâu: hiện tiến độ hoặc giải thích.
  - Luôn giữ layout ổn định và báo trạng thái bận cho screen reader (`aria-busy`).
- **INT-05** [Critical] Không thất bại im lặng:
  - Lỗi hiện gần nơi xảy ra.
  - Thông báo nêu nguyên nhân và cách khắc phục.
  - Có đường phục hồi (thử lại, sửa, liên hệ).
  - Request timeout có thông báo và nút thử lại.
- **INT-06** [Medium] Thao tác thành công có xác nhận ngắn: toast, checkmark hoặc đổi trạng thái.
- **INT-07** [High] Hành động phá huỷ hoặc không đảo ngược được:
  - Xác nhận trước khi thực hiện.
  - Nút dùng màu danger, tách khỏi hành động chính.
  - Thao tác xoá hàng loạt nên có undo (toast "Đã xoá · Hoàn tác").
- **INT-08** [Medium] Empty state giải thích vì sao trống và gợi ý bước tiếp theo. Tìm kiếm không có kết quả thì đưa ra gợi ý.
- **INT-09** [Medium] Toast:
  - Không chiếm focus.
  - Thông tin phụ tự ẩn sau 3–5s.
  - Thông báo có nút hành động hoặc thông tin quan trọng thì không tự ẩn quá nhanh.

## Tương tác

- **INT-10** [Medium] Mỗi màn hình chỉ có một CTA chính. Hành động phụ có độ nhấn thấp hơn.
- **INT-11** [High] Hành động quan trọng thực hiện bằng click/tap, không chỉ hiện khi hover. Phần tử bấm được có `cursor: pointer` (web).
- **INT-12** [Medium] Modal/sheet:
  - Có nút đóng rõ ràng.
  - Đóng khi còn thay đổi chưa lưu thì hỏi xác nhận.
  - Không dùng modal cho luồng điều hướng chính.
- **INT-13** [High] Badge và chip:
  - Badge thể hiện trạng thái, là phần tử tĩnh.
  - Chip/tag thể hiện giá trị hoặc hành động.
  - Chip tương tác là `button`, có trạng thái pressed/selected và focus. Không làm mọi "pill" đều bấm được.
- **INT-14** [Medium] Mobile:
  - Không chặn gesture hệ thống (vuốt back).
  - Không dùng vuốt ngang trên nội dung chính.
  - Thao tác vuốt có gợi ý nhìn thấy được.
  - Kéo thả có ngưỡng di chuyển, tránh kéo nhầm.
  - Pull-to-refresh chỉ bật ở nơi cần.
- **INT-15** [Medium] Nội dung do AI sinh:
  - Gắn nhãn rõ là do AI tạo.
  - Phản hồi dài thì stream dần, không để spinner chạy lâu.
  - Có cách phản hồi kết quả hoặc tạo lại.

## Forms

- **FORM-01** [Critical] Mỗi input có label luôn hiển thị và gắn với input (`<label for>` hoặc bọc input). Placeholder không thay thế label.
- **FORM-02** [High] Lỗi của từng trường:
  - Hiện ngay dưới trường.
  - Nêu cụ thể nguyên nhân và cách sửa (không chỉ "Không hợp lệ").
  - Gắn với trường bằng `aria-describedby`.
  - Phát qua live region.
- **FORM-03** [High] Submit thất bại:
  - Nhiều lỗi: hiện error summary ở đầu form, focus vào summary, mỗi mục link tới trường lỗi, vẫn giữ lỗi inline.
  - Một lỗi: focus vào trường lỗi.
- **FORM-04** [Medium] Thời điểm validate:
  - Validate khi blur, không validate theo từng phím, và không đợi đến lúc submit mới validate.
  - Trường đang báo lỗi thì xoá lỗi ngay khi người dùng sửa đúng.
- **FORM-05** [Medium] Đánh dấu trường bắt buộc (`*` kèm chú thích, hoặc chữ "bắt buộc") và dùng `required` / `aria-required`.
- **FORM-06** [Medium] Dùng đúng `type` / `inputmode` (email, tel, number, url) để hiện đúng bàn phím mobile, và đúng `autocomplete` để autofill hoạt động.
- **FORM-07** [Medium] Ô password có nút hiện/ẩn và cho phép paste.
- **FORM-08** [Medium] Input phức tạp có helper text cố định bên dưới (định dạng, giới hạn), không chỉ ghi trong placeholder.
- **FORM-09** [Medium] Bố cục form:
  - Trường liên quan được nhóm lại (`fieldset` / `legend` hoặc nhóm trực quan).
  - Form dài chia thành bước, có chỉ báo tiến độ và cho phép quay lại.
  - Tuỳ chọn nâng cao mở dần (progressive disclosure).
- **FORM-10** [Medium] Form dài tự lưu nháp. Rời trang khi còn thay đổi chưa lưu thì cảnh báo.
- **FORM-11** [Medium] Phân biệt disabled và read-only:
  - Disabled: giảm độ nhấn (opacity khoảng 0.38–0.5), không tương tác được, có thuộc tính `disabled`.
  - Read-only: vẫn focus, đọc và copy được.
- **FORM-12** [Medium] Input:
  - Trông rõ là ô nhập được (có viền hoặc nền).
  - Trên mobile: chiều cao ≥ 44px, cỡ chữ ≥ 16px (tránh iOS tự zoom).
