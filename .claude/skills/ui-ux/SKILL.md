---
name: ui-ux
description: "Bộ quy tắc UX/UI có ID (accessibility, interaction, form, layout, navigation, typography, màu, motion, hiệu năng, bảng và chart) cùng checklist trước bàn giao. Dùng khi thiết kế, code, kiểm thử hoặc review giao diện: màn hình, component, form, điều hướng, responsive, dark mode, animation."
---

# UI/UX Rules

Chọn lọc và chuyển thể từ [ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) (MIT).

## Chuẩn áp dụng

- Accessibility: **WCAG 2.2 AA**.
- Giá trị cụ thể (màu, spacing, cỡ chữ, breakpoint, duration) lấy từ **design token và component của dự án**. Con số trong skill này dùng khi token chưa quy định. Riêng ngưỡng accessibility là mức tối thiểu, token không được thấp hơn.

## Bảng ưu tiên

| # | Nhóm | Mức | File | Kiểm tra cốt lõi |
|---|---|---|---|---|
| 1 | Accessibility | CRITICAL | `references/accessibility.md` | Tương phản, bàn phím, focus, label, screen reader |
| 2 | Interaction & feedback | CRITICAL | `references/interaction-forms.md` | Đủ trạng thái, phản hồi thao tác, chống bấm trùng, lỗi có đường phục hồi |
| 3 | Forms | HIGH | `references/interaction-forms.md` | Label, lỗi inline, error summary, validate on blur |
| 4 | Layout & responsive | HIGH | `references/layout-navigation.md` | Không cuộn ngang, không layout shift, không cắt nội dung thiết yếu |
| 5 | Navigation | HIGH | `references/layout-navigation.md` | Back đúng, trạng thái trên URL, vị trí hiện tại rõ |
| 6 | Performance | HIGH | `references/performance-data.md` | Ảnh, bundle, danh sách dài, Core Web Vitals |
| 7 | Typography, màu, icon | MEDIUM | `references/visual-motion.md` | Semantic token, dark mode, thang chữ |
| 8 | Motion | MEDIUM | `references/visual-motion.md` | Có ý nghĩa, chỉ transform/opacity, không chặn input |
| 9 | Bảng, chart, định dạng dữ liệu | MEDIUM | `references/performance-data.md` | Locale, không phân biệt chỉ bằng màu, có bản thay thế cho screen reader |

Checklist trước bàn giao: `references/pre-delivery-checklist.md`.

**Chỉ đọc file của nhóm liên quan tới việc đang làm**, không nạp hết.

## Cách dùng

- **Thiết kế màn hình**: đọc nhóm liên quan, làm theo mục "Thiết kế màn hình" bên dưới.
- **Code giao diện**: đọc nhóm liên quan khi code. Trước khi báo xong, chạy `pre-delivery-checklist.md` và ghi kết quả vào báo cáo.
- **Kiểm thử / review**: dùng `pre-delivery-checklist.md` làm tiêu chí; mục Critical / High chưa đạt thì chưa xong. Bug vi phạm rule ghi kèm ID rule.

## Thiết kế màn hình

Làm trước khi code với màn hình mới có nhiều trạng thái hoặc tương tác: sơ đồ ghế, checkout, phòng chờ, check-in, dashboard, các form quản trị lớn. Màn hình đơn giản dựng từ component shadcn có sẵn thì bỏ qua bước này.

Ghi ngắn vào `docs/design/<slug>.md`:

```markdown
# Design: <tên>
## Tham chiếu: plan / AC / BR liên quan
## User flow (Mermaid, gồm nhánh lỗi và điểm thoát)
## Màn hình: <tên>
- Mục đích; wireframe ASCII
- Component: dùng lại (shadcn, component dự án) / làm mới (kèm lý do)
- Trạng thái: loading / empty / error / success / disabled / không có quyền
- Interaction và validation (báo lỗi khi nào, ở đâu); xác nhận hành động nguy hiểm
- Responsive (mobile / desktop); UX copy (nhãn, thông báo lỗi, empty state)
- Rule áp dụng (ID) và ngoại lệ (ID, lý do)
## Câu hỏi mở
```

- Nhất quán trước, sáng tạo sau: dùng token và component có sẵn; thiếu token thì đề xuất token mới, không hard-code.
- Thiết kế cho dữ liệu thật: text dài, danh sách rỗng, hàng nghìn ghế.
- Không viết code production trong bước này; prototype HTML chỉ khi wireframe không đủ diễn đạt.

## Mức độ và ngoại lệ

- Mức: **Critical > High > Medium > Low**.
- Rule Critical/High chưa đạt thì chưa được bàn giao, trừ khi là ngoại lệ được chấp thuận.
- Ngoại lệ có chủ đích phải ghi rõ ID rule, lý do và người chấp thuận trong `docs/design/<slug>.md` hoặc báo cáo.
- Trích dẫn rule theo ID, ví dụ: `A11Y-07`, `FORM-03`.
