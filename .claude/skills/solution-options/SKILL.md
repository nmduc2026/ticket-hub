---
name: solution-options
description: "Khung đưa ra và so sánh phương án kỹ thuật: 2–4 phương án, đánh giá trade-off theo tiêu chí cố định, pre-mortem, đề xuất có lý do. Dùng khi chọn cách tiếp cận cho feature, thay đổi kiến trúc, chọn thư viện hoặc công nghệ, thiết kế data model hay API, trước khi viết ADR hoặc giao việc cho dev."
---

# Solution Options

## Khi nào dùng

- **Dùng:** quyết định khó đảo ngược, ảnh hưởng nhiều module, hoặc có nhiều hướng làm hợp lý.
- **Không dùng:** quyết định nhỏ, dễ đổi lại. Khi đó chọn cách đơn giản nhất và ghi một dòng lý do.

## Quy trình

### 1. Đóng khung bài toán

- **Vấn đề** cần giải, gắn với AC hoặc mục tiêu trong brief.
- **Ràng buộc cứng**: bắt buộc phải thoả (tương thích ngược, bảo mật, thời hạn, stack hiện có...).
- **Mục tiêu mềm**: càng tốt càng hay.
- **Hiện trạng**: đọc code xem hệ thống đang giải bài toán tương tự thế nào.

### 2. Mở rộng rồi thu hẹp

- Liệt kê nhiều hướng, luôn có hai hướng sau:
  - **Không làm / làm tối thiểu**: có giải được phần lớn vấn đề không?
  - **Dùng cái đã có** trong codebase hoặc hạ tầng hiện tại.
- Loại phương án vi phạm ràng buộc cứng.
- Giữ lại **2–4 phương án thật sự khác nhau**, không phải các biến thể của cùng một ý.

### 3. Đánh giá theo tiêu chí

| Tiêu chí | Câu hỏi |
|---|---|
| Đáp ứng yêu cầu | Phủ hết AC chưa? Thiếu gì? |
| Độ phức tạp | Bao nhiêu code mới, khái niệm mới, phụ thuộc mới? |
| Nhất quán | Có hợp với kiến trúc và convention hiện tại không? |
| Rủi ro | Bảo mật, dữ liệu, hiệu năng, tích hợp |
| Chi phí đảo ngược | Nếu chọn sai, gỡ ra tốn bao nhiêu? |
| Vận hành | Deploy, monitor, chi phí hạ tầng, ai trực khi hỏng |
| Khả năng test | Có test tự động được không? |
| Thời gian | Bao lâu để giao được phiên bản dùng được? |

Mỗi ô ghi **Tốt / Chấp nhận / Kém** kèm một dòng lý do. Không chấm điểm số có trọng số, vì nó tạo cảm giác chính xác giả.

### 4. Pre-mortem

Với phương án đang dẫn đầu, giả sử 6 tháng sau nó thất bại:
- Nguyên nhân khả dĩ nhất là gì?
- Có giảm thiểu được không, bằng cách nào?
- Nếu không giảm thiểu được thì xếp hạng lại.

### 5. Đề xuất

- Chọn một phương án.
- Nêu **tiêu chí quyết định**: trong bối cảnh này tiêu chí nào nặng nhất, và vì sao.
- Nêu **cái giá chấp nhận**: phương án này kém ở đâu.
- Nêu **tín hiệu xem lại**: điều gì xảy ra thì nên đổi sang phương án khác.

### 6. Ghi lại

- Quyết định khó đảo ngược: viết thành ADR (`docs/adr/`).
- Còn lại: ghi vào mục "Bối cảnh kỹ thuật & cách tiếp cận" của plan.

## Mẫu

```markdown
## Bài toán
<vấn đề> · Ràng buộc cứng: ... · Mục tiêu mềm: ...

## Phương án
### A. <tên>: <mô tả 2–3 dòng>
### B. <tên>: ...
### C. <tên>: ...

## So sánh
| Tiêu chí | A | B | C |
|---|---|---|---|
| Đáp ứng yêu cầu | Tốt: ... | ... | ... |

## Pre-mortem cho <phương án dẫn đầu>

## Đề xuất: <phương án>
- Tiêu chí quyết định:
- Cái giá chấp nhận:
- Tín hiệu xem lại:
```

## Nguyên tắc

- Hai phương án ngang nhau thì chọn cái **đơn giản hơn** và **dễ đảo ngược hơn**. Cái đã có trong codebase được ưu tiên.
- Không chọn công nghệ mới chỉ vì nó mới.
- Tách rõ **sự thật** (đã đọc code, đã đo) và **phỏng đoán** (ước lượng tải, chi phí). Phỏng đoán quan trọng mà chưa chắc thì đề xuất một spike có giới hạn thời gian để kiểm chứng.
- Lựa chọn ảnh hưởng tới phạm vi, chi phí hoặc trải nghiệm người dùng thì **không tự quyết**: đưa vào câu hỏi mở `[BLOCKING]` kèm đề xuất của mình.
