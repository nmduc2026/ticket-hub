---
name: verification
description: "Quy tắc có bằng chứng mới được báo: trước khi khẳng định đã xong, đã pass, đã sửa hay đã đáp ứng AC, phải chạy đúng loại kiểm chứng cho khẳng định đó (test map vào AC, chạy thật luồng nghiệp vụ, red-green cho test hồi quy, gọi API so với contract) và đọc output thật. Dùng trước mọi báo cáo hoàn thành, trước khi bàn giao, và khi nhận báo cáo đã xong từ agent khác."
---

# Verification

Chuyển thể từ skill `verification-before-completion` của [obra/superpowers](https://github.com/obra/superpowers) (MIT), mở rộng sang kiểm chứng nghiệp vụ.

## Luật

**Không có bằng chứng mới, chạy ngay trong lần làm việc này, thì không được khẳng định.**

Build và lint chỉ chứng minh code biên dịch được. Chúng **không** chứng minh nghiệp vụ đúng.

## Cổng trước mỗi khẳng định

1. **Xác định**: khẳng định này cần loại bằng chứng nào? Tra bảng bên dưới.
2. **Chạy**: chạy đầy đủ, ở trạng thái code mới nhất. Không dùng kết quả của lần chạy trước khi sửa code.
3. **Đọc**: đọc toàn bộ output, exit code, số pass/fail/skip.
4. **So**: output có thật sự chứng minh khẳng định không?
   - Không: báo đúng trạng thái thật, kèm bằng chứng.
   - Có: khẳng định, kèm bằng chứng.

## Khẳng định → bằng chứng

### Kỹ thuật

| Khẳng định | Bằng chứng bắt buộc | Không đủ |
|---|---|---|
| Build được | Lệnh build trả exit code 0 | Lint pass |
| Lint / type-check sạch | Output 0 lỗi, chạy trên toàn bộ phạm vi | Chạy một phần |
| Test pass | Chạy lệnh test, đọc số pass/fail/skip | Kết quả lần trước; "chắc là pass" |

### Nghiệp vụ

| Khẳng định | Bằng chứng bắt buộc | Không đủ |
|---|---|---|
| Đáp ứng AC-xx | Test map vào AC-xx (tên/mô tả có ID) chạy pass; **hoặc** chạy thật luồng đó (gọi API, thao tác UI trên browser) và kết quả khớp phần *Then* | Build pass; test không liên quan pass |
| Business rule BR-xx đúng | Test các giá trị biên của rule (dưới, đúng bằng, trên ngưỡng) và trường hợp vi phạm | Chỉ test happy path |
| Luồng lỗi đúng | Gây lỗi thật (input sai, thiếu quyền, giả lập timeout) và thấy đúng thông báo / status code | Chỉ test luồng thành công |
| Phân quyền đúng | Gọi bằng user không có quyền, và bằng user khác chủ tài nguyên (IDOR), đều bị chặn | Chỉ test bằng admin |
| Dữ liệu ghi đúng | Truy vấn DB local/test sau thao tác, thấy đúng giá trị | API trả 200 |
| API khớp contract | Gọi endpoint thật, so status code, schema, format lỗi với `docs/api/` | Unit test của service |
| UI đúng thiết kế | Xem trên browser ở các breakpoint và trạng thái; chạy `pre-delivery-checklist` của `ui-ux` | Component test pass |

### Sửa lỗi

| Khẳng định | Bằng chứng bắt buộc | Không đủ |
|---|---|---|
| Đã sửa bug | Chạy lại đúng các bước tái hiện trong bug report, triệu chứng không còn | "Đã sửa dòng X" |
| Test hồi quy hợp lệ | **Red–green**: bỏ bản sửa thì test fail, thêm lại thì test pass | Test pass một lần |
| Không gây hồi quy | Chạy bộ test liên quan (không chỉ test mới) | Chỉ chạy test vừa viết |

### Hạ tầng

| Khẳng định | Bằng chứng bắt buộc | Không đủ |
|---|---|---|
| Pipeline / IaC đúng | Lint/validate, `plan` hoặc `--dry-run`, đọc phần thay đổi trong plan | File YAML đúng cú pháp |

### Phối hợp

| Khẳng định | Bằng chứng bắt buộc | Không đủ |
|---|---|---|
| Agent khác đã làm xong | Xem diff thật, chạy lại test liên quan | Báo cáo "đã xong" của agent |

## Bảng truy vết trước khi báo xong

Ghi vào báo cáo:

```markdown
| AC / BR | Bằng chứng (test nào / luồng nào / lệnh nào) | Kết quả |
|---|---|---|
| AC-01.1 | `login.spec.ts › đăng nhập đúng chuyển tới trang đơn hàng` | Pass |
| AC-01.3 | Gọi `POST /auth/login` lần thứ 6 → 423 + thông báo khoá | Pass |
| BR-01   | Chưa kiểm chứng: thiếu dữ liệu test cho trường hợp 15 phút | Chưa |
```

AC hoặc BR không có bằng chứng thì ghi **Chưa kiểm chứng** kèm lý do. Không làm tròn thành "đã đáp ứng".

## Khi không kiểm chứng được

Không có môi trường, thiếu dữ liệu, cần quyền: nói rõ cái gì chưa kiểm chứng, vì sao, và đề xuất cách kiểm chứng. Không thay bằng "chắc là đúng".

## Dấu hiệu đang báo sai

- Dùng các từ "chắc", "có lẽ", "trông đúng rồi", "lẽ ra phải chạy".
- Báo "xong" trước khi chạy kiểm chứng.
- Dùng kết quả của lần chạy trước, khi code đã sửa sau đó.
- Có test bị skip mà không nêu ra.
- Lấy báo cáo của agent khác làm bằng chứng.
- Lấy "build pass" làm bằng chứng cho nghiệp vụ.
