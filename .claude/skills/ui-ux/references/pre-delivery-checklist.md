# Pre-Delivery Checklist

Dùng trước khi bàn giao giao diện (dev-fe), khi kiểm thử UI (tester) và khi review cuối (leader). Mục chưa đạt thì ghi ID rule, lý do và hướng xử lý vào báo cáo.

## Cách kiểm tra

- [ ] Xem ở độ rộng 375px, 768px, 1440px và ở chế độ landscape trên mobile
- [ ] Thao tác toàn bộ luồng chỉ bằng bàn phím
- [ ] Zoom 200% và độ rộng 320px
- [ ] Bật `prefers-reduced-motion`
- [ ] Light và dark mode (nếu sản phẩm có dark mode), kiểm tra riêng từng theme
- [ ] Chạy công cụ kiểm tra accessibility tự động nếu dự án có (axe, Lighthouse...). Công cụ không thay thế được kiểm tra thủ công.

## Critical

- [ ] Tương phản chữ đạt chuẩn ở mọi theme — A11Y-01
- [ ] Mọi thao tác dùng được bằng bàn phím, focus luôn nhìn thấy được — A11Y-06, A11Y-07
- [ ] Control là phần tử ngữ nghĩa, không phải `div` click được — A11Y-12
- [ ] Không mất nội dung, không cuộn ngang ở zoom 200% / 320px — A11Y-18
- [ ] Đăng nhập cho phép paste và password manager — A11Y-22
- [ ] Đủ trạng thái loading / empty / error / success / không có quyền — INT-01
- [ ] Không có lỗi im lặng; lỗi có đường phục hồi — INT-05
- [ ] Mỗi input có label hiển thị — FORM-01
- [ ] Không cắt mất thông tin thiết yếu — LAY-10

## High

- [ ] Không truyền tải thông tin chỉ bằng màu — A11Y-03, DATA-05
- [ ] Icon và ảnh có text alternative phù hợp; nút chỉ có icon có `aria-label` — A11Y-04, A11Y-05
- [ ] Sticky UI không che control đang focus — A11Y-08
- [ ] Modal quản lý focus đúng (vào, giữ, `Esc`, trả focus) — A11Y-09
- [ ] Thông báo động phát qua live region — A11Y-14
- [ ] Vùng bấm đủ lớn; kéo thả có cách thay thế — A11Y-15, A11Y-16
- [ ] Tôn trọng reduced motion; carousel có pause — A11Y-20, A11Y-21
- [ ] Control có đủ trạng thái hover/pressed/focus/disabled — INT-02
- [ ] Nút submit chống gửi trùng — INT-03
- [ ] Hành động phá huỷ có xác nhận — INT-07
- [ ] Lỗi form hiện inline; submit lỗi có error summary hoặc focus vào trường lỗi — FORM-02, FORM-03
- [ ] Không cuộn ngang, không layout shift, nội dung dài không phá layout — LAY-02, LAY-08, LAY-09
- [ ] Phần tử fixed/sticky không che nội dung; tôn trọng safe area — LAY-05
- [ ] Back, URL và vị trí hiện tại trên điều hướng hoạt động đúng — NAV-01, NAV-02, NAV-03
- [ ] Dùng semantic token; dark mode được kiểm tra riêng — VIS-05, VIS-06
- [ ] Animation chỉ dùng transform/opacity, không chặn input — MOT-02, MOT-03
- [ ] Ảnh tối ưu; danh sách dài được virtualize hoặc phân trang — PERF-01, PERF-07
