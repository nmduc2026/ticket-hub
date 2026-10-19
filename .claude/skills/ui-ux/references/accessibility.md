# Accessibility (A11Y)

Chuẩn: WCAG 2.2 AA.

## Nhận biết & tương phản

- **A11Y-01** [Critical] Tương phản chữ ≥ 4.5:1. Chữ lớn (≥ 24px, hoặc ≥ 18.66px đậm) ≥ 3:1. Kiểm tra riêng cho từng theme light/dark.
- **A11Y-02** [High] Thành phần non-text mang thông tin (icon có nghĩa, viền input, focus ring, trạng thái control) tương phản ≥ 3:1 với màu nền kề bên.
- **A11Y-03** [High] Không truyền tải thông tin chỉ bằng màu: lỗi, thành công, trạng thái, series trong chart phải kèm icon, chữ hoặc pattern.
- **A11Y-04** [High] Ảnh có nghĩa có `alt` mô tả nội dung. Ảnh trang trí dùng `alt=""`.
- **A11Y-05** [High] Icon:
  - Icon trang trí đứng cạnh chữ: ẩn khỏi accessibility tree (`aria-hidden="true"`).
  - Icon đứng một mình mang nghĩa: có text alternative.
  - Nút chỉ có icon: có accessible name (`aria-label`) và trạng thái `aria-pressed` / `aria-expanded` / `aria-selected` nếu có.

## Bàn phím & focus

- **A11Y-06** [Critical] Mọi thao tác dùng được chỉ bằng bàn phím. Thứ tự Tab khớp thứ tự hiển thị. Không có keyboard trap.
- **A11Y-07** [Critical] Focus luôn nhìn thấy được trên mọi control, kể cả control trong modal. Không xoá `outline` mà không có chỉ báo thay thế.
- **A11Y-08** [High] Header/footer sticky, banner, chat widget không được che hoàn toàn control đang focus. Dùng `scroll-padding` cho phần tử sticky.
- **A11Y-09** [High] Modal/dialog:
  - Khi mở: focus chuyển vào modal.
  - Khi đang mở: focus giữ trong modal, `Esc` đóng modal.
  - Khi đóng: trả focus về phần tử đã mở modal.
- **A11Y-10** [Medium] Trang có nhiều điều hướng có skip link "Bỏ qua tới nội dung chính".
- **A11Y-11** [Medium] Chuyển route trong SPA: đưa focus tới heading hoặc vùng nội dung chính, cập nhật `document.title`.

## Cấu trúc & screen reader

- **A11Y-12** [Critical] Dùng phần tử HTML ngữ nghĩa (`button`, `a`, `nav`, `main`, `label`, `table`...) trước, ARIA sau. Không dùng `div`/`span` gắn `onClick` làm control.
- **A11Y-13** [Medium] Heading theo thứ tự h1 → h6, không nhảy cấp, không dùng thẻ heading chỉ để làm chữ to.
- **A11Y-14** [High] Thông báo động (lỗi form, toast, số lượng thay đổi) phát qua live region, không chuyển focus:
  - Thông báo thường: `role="status"` / `aria-live="polite"`.
  - Lỗi khẩn: `role="alert"`.
  - Nội dung đọc thành câu có ngữ cảnh ("3 sản phẩm trong giỏ"), không đọc mỗi con số.
  - Mỗi vùng chỉ dùng một live region, không để nhiều badge cùng phát.

## Kích thước vùng bấm & thao tác

- **A11Y-15** [High] Vùng bấm:
  - Web: tối thiểu 24×24 CSS px (mức AA).
  - Control chính trên màn hình cảm ứng: khuyến nghị ≥ 44×44px.
  - App native: 44pt (iOS) / 48dp (Android).
  - Icon nhỏ thì mở rộng vùng bấm bằng padding.
  - Các vùng bấm liền kề cách nhau ≥ 8px.
- **A11Y-16** [High] Thao tác kéo thả (sắp xếp, resize, chọn vùng) phải có cách thay thế bằng một cú bấm (nút, menu) và bằng bàn phím.
- **A11Y-17** [Medium] Không yêu cầu bấm chính xác tới từng pixel. Thao tác chỉ dùng gesture (vuốt, chụm) phải có control hiển thị thay thế.

## Phóng to & reflow

- **A11Y-18** [Critical] Ở zoom 200% và chiều rộng 320 CSS px, nội dung không bị mất và không phải cuộn ngang (trừ bảng, chart, bản đồ). Viewport meta không được khoá zoom (`user-scalable=no`, `maximum-scale=1`).
- **A11Y-19** [High] Hộp chứa chữ không cố định chiều cao. Dùng `line-height` không đơn vị, để chữ không bị cắt khi người dùng tăng cỡ chữ hoặc khoảng cách chữ.

## Chuyển động & nội dung tự chạy

- **A11Y-20** [High] Tôn trọng `prefers-reduced-motion`: tắt parallax và scroll-jacking, giảm animation lớn, hiển thị ngay trạng thái cuối.
- **A11Y-21** [High] Carousel và nội dung tự chuyển có nút pause/stop, tự dừng khi được focus/hover hoặc khi bật reduced motion. Video không tự phát kèm âm thanh, có nút dừng và phụ đề.

## Xác thực & nhập liệu

- **A11Y-22** [Critical] Đăng nhập:
  - Không chặn paste và password manager.
  - Có phương thức không dựa vào trí nhớ hay chép tay (passkey, OAuth, magic link).
  - Không bắt chép tay OTP mà không có lựa chọn khác.
- **A11Y-23** [Medium] Trong cùng một quy trình, không bắt nhập lại thông tin đã nhập. Tự điền sẵn hoặc cho chọn lại giá trị cũ.
- **A11Y-24** [Medium] Cơ chế trợ giúp lặp lại trên nhiều trang (liên hệ, help, chat) giữ cùng vị trí tương đối.
