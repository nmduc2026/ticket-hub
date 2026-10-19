---
name: brainstorm
description: "Làm rõ một ý tưởng hoặc yêu cầu còn mơ hồ bằng cách hỏi đáp trực tiếp với người dùng, rồi chốt thành bản brief trước khi giao cho team. Dùng khi yêu cầu chưa rõ mục tiêu, phạm vi hay tiêu chí thành công, hoặc khi người dùng gọi /brainstorm. Chạy ở session chính, không dùng trong subagent."
---

# Brainstorm

Chuyển thể từ skill `brainstorming` của [obra/superpowers](https://github.com/obra/superpowers) (MIT).

Người dùng thường chưa nghĩ xong mình muốn gì. Câu hỏi đúng giúp họ nghĩ cho trọn. Khi hiểu được **tại sao**, team sẽ tự đưa ra hàng trăm quyết định nhỏ theo đúng cách người dùng muốn.

Skill này chạy ở **session chính**, vì subagent không hỏi đáp qua lại được với người dùng. Kết quả là bản brief để `ba` viết thành đặc tả.

## 1. Có cần brainstorm không?

Tự hỏi: mình đã biết người dùng muốn gì và tại sao chưa?

- **Yêu cầu đã đủ rõ:** bỏ qua skill này, đi thẳng `team-workflow`.
- **Còn thiếu điều quan trọng:** bắt đầu hỏi.

## 2. Tin nhắn đầu tiên

Chỉ gồm hai thứ, theo đúng thứ tự:

1. Một dòng cho biết nếu người dùng muốn bỏ qua phần hỏi và bắt đầu luôn thì cứ nói.
   - Nếu họ chọn bỏ qua: nêu một dòng về các lựa chọn lớn sẽ áp dụng, rồi chuyển sang `team-workflow`.
2. Một câu hỏi mở để họ mô tả, bám vào một tình huống cụ thể. Ví dụ: "Lần gần nhất anh/chị cần đến tính năng này là trong tình huống nào?"

## 3. Cách hỏi

- **Mỗi tin nhắn kết thúc bằng đúng một câu hỏi.** Không kèm danh sách đáp án mẫu.
- Dùng ngôn ngữ và từ vựng của người dùng; không dùng thuật ngữ quy trình.
- **Hỏi mở trước.** Lời của họ chứa ý định mà lựa chọn của mình không có.
  - Chỉ đưa vài lựa chọn khi họ bí.
  - Khi họ nói "bạn nghĩ sao", đề xuất một phương án cụ thể.
- Một phỏng đoán để họ sửa ("Tôi đoán là vì X hay gây phiền?") thường hiệu quả hơn một câu hỏi.
- Hỏi sớm xem những lựa chọn về cách làm (nền tảng, công nghệ, tích hợp) họ muốn tự quyết hay để team quyết. Có hệ thống sẵn thì xây trên cái đang có.

### Những vùng cần phủ

Đây là bản đồ, không phải kịch bản. Không đọc to danh sách này thành câu hỏi.

- Vấn đề là gì, ai gặp, đang xử lý thế nào
- Tại sao làm bây giờ; thành công trông như thế nào (đo được thì càng tốt)
- Mục tiêu, ngoài phạm vi, và **anti-goal** (kết quả "chạy đúng" nhưng vẫn là thất bại)
- Hiện trạng: đã có gì, đã thử gì
- Ràng buộc: thời hạn, kỹ thuật, pháp lý, ngân sách
- Ví dụ đã thấy ở nơi khác, thích hoặc ghét
- Những chi tiết họ đặc biệt quan tâm

### Khảo sát khi cần

Khi đã có chút nền tảng, đề nghị xem code, tài liệu hoặc dữ liệu liên quan. Nói rõ sẽ tìm gì; người dùng quyết định có cho xem hay không.

## 4. Nhắc lại từng phần

- Hiểu đủ một phần (ví dụ: cho ai và để làm gì) thì mô tả lại phần đó trong khoảng 150–250 từ, rồi hỏi tiếp phần sau.
- Nhắc lại và hỏi tiếp xen kẽ suốt cuộc trao đổi.
- Đánh dấu rõ chỗ nào là **phỏng đoán của mình**; chỉ ghi là của người dùng những gì họ đã nói.
- Cuối mỗi phần, hỏi: có gì sai hoặc thiếu không?

## 5. Xác định quy mô

Khi đã hiểu, chọn quy mô và nói ra bằng lời đơn giản. Người dùng có thể đổi.

| Quy mô | Mô tả đầy đủ nằm ở | Bước tiếp |
|---|---|---|
| Việc nhỏ, rõ ràng | chính yêu cầu | giao thẳng cho `dev-fe` / `dev-be` |
| Thay đổi vừa | tóm tắt trong chat | `team-workflow` với pipeline ngắn |
| Feature / dự án | bản brief | `team-workflow` đầy đủ |

- Làm giữa chừng mà việc phình ra thì dừng lại, nói rõ, và nâng quy mô.
- Nếu thực chất là nhiều việc độc lập thì nói ra, thống nhất thứ tự, và brainstorm từng việc một.

## 6. Bản brief

Ghi vào `docs/briefs/YYYY-MM-DD-<slug>.md`, đủ để `ba` viết đặc tả mà không phải hỏi lại người dùng:

```markdown
# Brief: <tên>
## Vấn đề & người dùng
## Mục tiêu & chỉ số thành công
## Ngoài phạm vi & anti-goal
## Hiện trạng
## Ràng buộc
## Quyết định người dùng đã chốt
## Quyết định do Claude đề xuất (người dùng chưa xác nhận riêng)
## Câu hỏi còn mở (cho ba / leader)
```

## 7. Cổng duyệt (bắt buộc)

**Chưa dispatch agent nào để thiết kế hay code cho đến khi người dùng duyệt** brief (với feature) hoặc mô tả trong chat (với thay đổi vừa). Đọc và khảo sát thì được.

- Nếu người dùng chọn bỏ qua phần hỏi thì không cần cổng này.
- "Nghe hay đấy" chỉ là duyệt phần đã trình bày. Mô tả chưa viết ra thì chưa được duyệt.

Duyệt xong: gọi `team-workflow` và truyền đường dẫn brief làm input cho `ba`.

## 8. Dấu hiệu đang làm sai

| Suy nghĩ | Thực tế |
|---|---|
| "Đưa sẵn vài lựa chọn cho họ đỡ mất công" | Lựa chọn dẫn dắt người dùng. Để họ mô tả trước. |
| "Tự điền mặc định hợp lý cho nhanh" | Được, nhưng phải nói ra. Mặc định họ không nghe thấy thì họ không phản đối được. |
| "Giải thích quy trình trước đã" | Cứ hỏi đi. Quy trình tự lộ ra. |
| "Hỏi một loạt câu cho nhanh" | Mỗi lần một câu. Câu trả lời trước thay đổi câu hỏi sau. |
