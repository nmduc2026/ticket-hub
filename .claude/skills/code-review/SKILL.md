---
name: code-review
description: "Quy trình review code và nhận review: đối chiếu yêu cầu, tìm bug, lỗ hổng bảo mật, lỗi bị nuốt im lặng, vấn đề kiến trúc và test; chỉ báo phát hiện chắc chắn kèm file:line và kịch bản lỗi; xử lý góp ý review có kiểm chứng. Dùng khi review diff hoặc PR, tự review trước khi bàn giao, review cuối feature, hoặc khi nhận góp ý review cần sửa."
---

# Code Review

Tham khảo [obra/superpowers](https://github.com/obra/superpowers) (MIT) và ý tưởng từ plugin code review của Anthropic.

Gồm ba phần:
- **A. Review code**: dùng khi review thay đổi của người khác, hoặc review cuối feature.
- **B. Tự review**: dùng trước khi bàn giao code của chính mình.
- **C. Nhận review**: dùng khi được góp ý và cần sửa.

---

## A. Review code

### Phạm vi & đầu vào

- Mặc định review **diff của thay đổi** (`git diff`, `git diff <base>...HEAD`). Đọc thêm code xung quanh khi cần để xác nhận một phát hiện.
- Đầu vào: AC (`docs/requirements/`), API contract (`docs/api/`), plan, `CLAUDE.md` và skill convention của dự án.
- Review là **chỉ đọc**: không sửa code, không đổi branch hay HEAD.

### Quy trình

1. **Hiểu ý định**: đọc AC, plan, mô tả thay đổi; liệt kê những gì thay đổi phải đạt được.
2. **Đối chiếu yêu cầu**:
   - Từng AC đã được đáp ứng chưa? Còn thiếu gì?
   - Chỗ nào làm khác plan? Đó là cải tiến có lý do, hay đi lệch?
   - Với hành vi đặc tả không nói tới: đánh giá theo kỳ vọng hợp lý của người dùng. Đặc tả im lặng không có nghĩa là được phép làm sai.
3. **Logic & bug**:
   - Giá trị biên, null/undefined, off-by-one.
   - Race condition, async thiếu `await`.
   - Sai kiểu dữ liệu, state không nhất quán.
4. **Bảo mật**:
   - Validate input; injection.
   - Authorization trên từng tài nguyên (IDOR); XSS.
   - Secret hoặc PII trong code hay log.
5. **Lỗi bị nuốt im lặng**:
   - `catch` rỗng; `catch` quá rộng che mất lỗi không liên quan.
   - Chỉ log rồi chạy tiếp trong khi kết quả đã sai.
   - Trả về `null` hoặc giá trị mặc định khi lỗi mà không báo.
   - Fallback che lỗi, nhất là fallback sang mock hay dữ liệu giả ngoài test.
   - Optional chaining bỏ qua một thao tác quan trọng.
   - Retry hết lượt mà không báo cho ai.
   - Thông báo lỗi cho người dùng phải nói được chuyện gì xảy ra và nên làm gì tiếp.
6. **Kiến trúc & thiết kế**:
   - Có hợp với kiến trúc và convention của dự án không?
   - Trách nhiệm có được tách rõ không?
   - Có trùng lặp với helper đã có không (tìm trong codebase)?
   - Có abstraction thừa không?
7. **Dữ liệu & production**:
   - Migration, tương thích ngược.
   - Hiệu năng: N+1, query không giới hạn.
   - Log và metric đủ để vận hành.
8. **Test**:
   - Test kiểm tra hành vi thật, hay chỉ kiểm tra mock?
   - Có phủ edge case và luồng lỗi không?
   - Bug fix có test tái hiện bug không?
   - Test có pass không (chạy nếu được)?

### Chỉ báo phát hiện chắc chắn

**Báo khi:**
- Chắc chắn là lỗi và chỉ ra được kịch bản cụ thể: input hoặc trạng thái nào dẫn tới kết quả sai.
- Vi phạm rõ một quy tắc trong `CLAUDE.md` hoặc skill của dự án, và trích được quy tắc đó.
- Thiếu so với AC.

**Không báo:**
- Lỗi có sẵn từ trước, nằm ngoài diff. Nếu nghiêm trọng thì ghi riêng ở mục "Ngoài phạm vi".
- Thứ linter, formatter hay type-checker sẽ bắt.
- Góp ý theo sở thích cá nhân.
- "Có thể có vấn đề" mà không chỉ ra được kịch bản.
- Vi phạm đã được tắt có chủ đích trong code (comment ignore kèm lý do).

**Trước khi báo, tự kiểm chứng:** đọc code liên quan để chắc lỗi có thật. Ví dụ: hàm "chưa được định nghĩa" có thể đã được import ở chỗ khác.

### Mức độ

| Mức | Gồm | Ảnh hưởng tới kết luận |
|---|---|---|
| **Critical** | Bug chắc chắn, lỗ hổng bảo mật, mất hoặc sai dữ liệu, phá chức năng, vi phạm rule Critical của `ui-ux` | Phải sửa |
| **Important** | Không đạt AC, thiết kế sai, lỗi bị nuốt, thiếu test cho logic quan trọng, phá tương thích ngược, vi phạm rule High của `ui-ux` | Phải sửa trước khi bàn giao |
| **Minor** | Cải thiện rõ ràng nhưng không chặn: tên gây hiểu nhầm, trùng lặp nhỏ | Ghi nhận |

Không thổi phồng mức độ của góp ý nhỏ.

### Mẫu báo cáo review

```markdown
## Phạm vi đã review (commit/diff, danh sách file)
## Điểm làm tốt (cụ thể, có file:line)
## Phát hiện
### Critical
- `file:line`: <vấn đề>. Kịch bản: <input/trạng thái → kết quả sai>. Đề xuất: <cách sửa>
### Important
### Minor
## Đã cân nhắc nhưng không báo (mỗi dòng một mục kèm lý do)
## Ngoài phạm vi (lỗi có sẵn nghiêm trọng, nếu có)
## Kết luận: APPROVED / CHANGES REQUESTED (lý do trong 1–2 câu)
```

Còn Critical hoặc Important thì kết luận là **CHANGES REQUESTED**.

---

## B. Tự review trước khi bàn giao

1. Chạy phần A trên diff của chính mình.
2. Sửa hết Critical và Important.
3. Minor còn lại thì ghi trong báo cáo bàn giao.

---

## C. Nhận review

1. **Đọc hết** góp ý trước khi sửa bất cứ mục nào.
2. **Hiểu**: diễn đạt lại từng mục. Mục nào chưa rõ thì hỏi lại **tất cả** mục chưa rõ trước khi sửa, vì các mục có thể liên quan nhau.
3. **Kiểm chứng với code thực tế**:
   - Góp ý có đúng với codebase này không?
   - Sửa theo có làm hỏng chức năng khác không?
   - Cách làm hiện tại có lý do không?
4. **Phản biện bằng lý do kỹ thuật** khi góp ý không đúng:
   - Sửa theo sẽ làm hỏng chức năng khác.
   - Người review thiếu bối cảnh.
   - Đề xuất xây thứ chưa ai dùng (YAGNI): tìm trong code xem có chỗ nào gọi tới không.
   - Góp ý mâu thuẫn với ADR hoặc quyết định đã chốt: chuyển cho leader hoặc người dùng quyết.
5. **Sửa theo thứ tự**: Critical → mục sửa đơn giản → mục sửa phức tạp. Chạy test sau mỗi mục và kiểm tra hồi quy.
6. **Phản hồi** từng mục: đã sửa gì, ở đâu; hoặc lý do không sửa.
   - Không đồng ý xã giao kiểu "Bạn hoàn toàn đúng!". Hành động chứng minh là đã nghe.
   - Nếu mình phản biện sai: nhận ngắn gọn rồi sửa.
