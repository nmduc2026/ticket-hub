# Performance & Data Display (PERF, DATA)

## Performance

- **PERF-01** [High] Ảnh:
  - Dùng định dạng WebP/AVIF, `srcset` / `sizes` đúng kích thước hiển thị.
  - Ảnh dưới màn hình đầu dùng `loading="lazy"`.
  - Ảnh hero/LCP không lazy load.
- **PERF-02** [High] Core Web Vitals: LCP ≤ 2.5s, INP ≤ 200ms, CLS < 0.1 (xem LAY-08).
- **PERF-03** [Medium] Bundle:
  - Split code theo route/feature.
  - Lazy load component nặng chưa cần ngay.
  - Theo dõi bundle size, không để phình to mà không ai kiểm soát.
- **PERF-04** [Medium] Script bên thứ ba tải `async` / `defer`. Thường xuyên rà soát và bỏ script không cần.
- **PERF-05** [Medium] Font:
  - Dùng `font-display: swap` hoặc `optional`.
  - Fallback font có metric gần giống để tránh layout shift.
  - Chỉ preload font thật sự quan trọng.
- **PERF-06** [Medium] CSS/JS không chặn lần render đầu. CSS cho phần đầu trang được ưu tiên tải trước.
- **PERF-07** [High] Danh sách từ khoảng 50 mục trở lên dùng virtualization hoặc phân trang.
- **PERF-08** [Medium] Main thread:
  - Sự kiện tần suất cao (scroll, resize, ô tìm kiếm) dùng debounce/throttle.
  - Gom đọc rồi ghi DOM theo đợt, tránh layout thrashing.
  - Tác vụ nặng đưa ra khỏi main thread.
- **PERF-09** [Medium] Mạng chậm hoặc offline: hiện trạng thái kết nối và có chế độ giảm tải (ảnh nhẹ hơn, ít animation hơn).
- **PERF-10** [Low] Video và asset nặng được nén và lazy load, không autoplay. Asset tĩnh có hash trong tên file để cache dài hạn.

## Định dạng dữ liệu & bảng

- **DATA-01** [Medium] Định dạng theo locale (`Intl`):
  - Số có phân tách hàng nghìn.
  - Ngày không mơ hồ (tránh nhầm giữa `03/04` và `04/03`).
  - Tiền tệ có đơn vị.
- **DATA-02** [Medium] Bảng:
  - Header rõ ràng.
  - Cột sắp xếp được có `aria-sort`.
  - Cột số căn phải, dùng tabular figures.
  - Trên mobile: cuộn ngang trong vùng chứa riêng hoặc chuyển sang dạng card.
- **DATA-03** [Low] Màn hình mà người dùng thường thao tác nhiều dòng thì hỗ trợ chọn nhiều và thao tác hàng loạt.

## Chart

- **DATA-04** [Medium] Chọn loại chart theo dữ liệu:
  - Xu hướng: line.
  - So sánh: bar.
  - Tỉ lệ: pie/donut, tối đa 5 nhóm; nhiều hơn thì dùng bar.
- **DATA-05** [High] Không phân biệt series chỉ bằng màu (tránh cặp đỏ/xanh lá):
  - Thêm pattern, hình dạng marker hoặc nhãn trực tiếp.
  - Đường/cột tương phản ≥ 3:1 với nền; nhãn chữ ≥ 4.5:1.
- **DATA-06** [High] Chart dùng được với screen reader và bàn phím:
  - Có tóm tắt bằng chữ insight chính, và/hoặc bảng dữ liệu thay thế.
  - Phần tử tương tác (điểm, cột) focus được bằng bàn phím.
  - Tooltip không chỉ hiện khi hover.
- **DATA-07** [Medium] Thành phần chart:
  - Legend đặt gần chart; nhiều series thì cho click legend để bật/tắt.
  - Trục có nhãn và đơn vị; time series ghi rõ đơn vị thời gian.
  - Tooltip hiện giá trị chính xác.
  - Grid line nhạt màu.
- **DATA-08** [Medium] Chart có đủ trạng thái:
  - Loading: skeleton, không hiện khung trục trống.
  - Empty: "Chưa có dữ liệu" kèm hướng dẫn.
  - Error: thông báo kèm nút thử lại.
- **DATA-09** [Medium] Trên mobile, chart được đơn giản hoá (ít tick hơn, đổi sang bar ngang) và nhãn trục không chồng lên nhau.
- **DATA-10** [Medium] Trên 1000 điểm dữ liệu thì gộp hoặc lấy mẫu, có drill-down để xem chi tiết. Drill-down luôn có đường quay lại.
