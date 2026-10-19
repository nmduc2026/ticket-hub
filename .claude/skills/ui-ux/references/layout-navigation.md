# Layout, Responsive & Navigation (LAY, NAV)

## Layout & responsive

- **LAY-01** [High] Thiết kế mobile-first, breakpoint theo token của dự án. Kiểm tra tối thiểu ở các độ rộng 320/375, 768, 1024, 1440 và ở chế độ landscape.
- **LAY-02** [High] Mobile không có cuộn ngang:
  - Ảnh dùng `max-width: 100%`.
  - Bảng rộng cuộn ngang trong vùng chứa riêng, hoặc chuyển sang dạng card.
- **LAY-03** [Medium] Spacing:
  - Lấy từ thang 4/8px của token.
  - Khoảng cách giữa các cấp (component, section, page) phân bậc rõ, ví dụ 16/24/32/48.
- **LAY-04** [Medium] Văn bản dài giới hạn khoảng 60–75 ký tự mỗi dòng (35–60 trên mobile). Container có `max-width` nhất quán giữa các trang.
- **LAY-05** [High] Phần tử fixed/sticky (header, bottom bar, CTA bar):
  - Chừa padding để không che nội dung bên dưới.
  - Tôn trọng safe area trên mobile (notch, thanh gesture).
- **LAY-06** [Medium] z-index:
  - Dùng thang định nghĩa sẵn, ví dụ 0/10/20/40/100/1000.
  - Không dùng số tuỳ ý như `9999`.
  - Nắm rõ cái gì tạo stacking context mới.
- **LAY-07** [Medium] Màn hình full-height trên mobile dùng `dvh` (`min-height: 100dvh`) thay cho `100vh`.
- **LAY-08** [High] Không gây layout shift:
  - Ảnh và media có kích thước hoặc `aspect-ratio`.
  - Chừa sẵn chỗ cho nội dung async (badge, dòng lỗi validate, skeleton → nội dung thật).
- **LAY-09** [High] Nội dung dài không đoán trước được (URL, ID, tên người dùng) không được phá layout:
  - Dùng `overflow-wrap: anywhere`.
  - Phần tử con của flex/grid đặt `min-width: 0`.
  - Không dùng `word-break: break-all` cho văn bản thường.
- **LAY-10** [Critical] Không cắt (truncate) thông tin thiết yếu: heading, nút hành động, thông báo lỗi, cảnh báo an toàn, tên dùng để phân biệt các mục.
  - Ưu tiên cho xuống dòng.
  - Khi buộc phải cắt: có dấu ellipsis và cách xem đầy đủ không phụ thuộc hover (dùng được bằng bàn phím và cảm ứng).
- **LAY-11** [High] Nhóm chip/filter:
  - Xuống dòng trước khi thu nhỏ chữ.
  - Phần tràn gom thành "+n" bấm được để xem hết.
  - Một chip đơn lẻ giữ nhãn trên một dòng.
- **LAY-12** [Medium] Không dùng `overflow: hidden` tuỳ tiện làm mất nội dung. Tránh vùng cuộn lồng nhau.
- **LAY-13** [Medium] Trên mobile, nội dung cốt lõi đặt lên trước, nội dung phụ thu gọn lại.
- **LAY-14** [Medium] Tạo thứ bậc thị giác bằng kích thước, khoảng cách, độ đậm và tương phản, không chỉ bằng màu.

## Navigation

- **NAV-01** [High] Vị trí hiện tại được đánh dấu rõ trên điều hướng (màu kèm độ đậm hoặc chỉ báo) và có `aria-current="page"`.
- **NAV-02** [High] Back hoạt động đúng kỳ vọng:
  - Không phá lịch sử trình duyệt.
  - Khôi phục vị trí cuộn, filter và dữ liệu đã nhập.
  - Không tự nhảy về trang chủ.
- **NAV-03** [High] Màn hình quan trọng và trạng thái có ý nghĩa (filter, tab, trang, mục đang xem) được phản ánh trên URL, để chia sẻ và deep link được.
- **NAV-04** [Medium] Vị trí và cấu trúc điều hướng nhất quán giữa các trang. Không trộn tab, sidebar và bottom nav ở cùng một cấp.
- **NAV-05** [Medium] Điều hướng chính và phụ:
  - Tách biệt rõ ràng.
  - Màn hình ≥ 1024px ưu tiên sidebar, mobile dùng bottom nav hoặc top nav.
  - Bottom nav tối đa 5 mục, chỉ cho màn hình cấp cao nhất, mỗi mục có cả icon lẫn nhãn.
- **NAV-06** [Medium] Cấu trúc sâu từ 3 cấp trở lên có breadcrumb.
- **NAV-07** [Medium] Hết chỗ thì gom hành động vào menu overflow ("…"), không nhồi nhét.
- **NAV-08** [Medium] Hành động nguy hiểm (xoá tài khoản, đăng xuất) tách biệt về vị trí và màu sắc khỏi các mục điều hướng thông thường.
- **NAV-09** [Medium] Điều hướng luôn với tới được từ các trang sâu. Mục không khả dụng thì giải thích lý do thay vì ẩn đi, trừ khi việc ẩn là yêu cầu phân quyền hoặc bảo mật.
- **NAV-10** [Medium] Tìm kiếm dễ tiếp cận, có gợi ý hoặc autocomplete, và trạng thái không có kết quả kèm gợi ý.
- **NAV-11** [Low] Onboarding/tour có nút Bỏ qua và Quay lại.
- **NAV-12** [Low] Badge trên mục điều hướng dùng tiết chế và biến mất khi người dùng đã xem.
