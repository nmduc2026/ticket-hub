---
name: systematic-debugging
description: "Quy trình tìm nguyên nhân gốc trước khi sửa: điều tra và tái hiện, so sánh với code đang chạy đúng, kiểm chứng từng giả thuyết, rồi mới sửa kèm test tái hiện; dừng lại sau 3 lần sửa không thành. Dùng khi gặp bug, test fail, hành vi bất thường, lỗi build hoặc lỗi tích hợp, trước khi đề xuất bản sửa; và khi cần tái hiện, khoanh vùng bug để viết bug report."
---

# Systematic Debugging

Chuyển thể từ skill `systematic-debugging` của [obra/superpowers](https://github.com/obra/superpowers) (MIT).

## Luật

**Chưa tìm ra nguyên nhân gốc thì chưa được sửa.** Sửa triệu chứng là thất bại.

Áp dụng cả khi bug "trông đơn giản" và khi đang gấp. Đoán rồi sửa thử còn chậm hơn đi theo quy trình.

## 4 giai đoạn

Phải xong giai đoạn trước mới được sang giai đoạn sau.

### Giai đoạn 1: Điều tra

1. **Đọc kỹ lỗi**: toàn bộ thông báo, stack trace, số dòng, mã lỗi. Đừng bỏ qua warning.
2. **Tái hiện ổn định**:
   - Ghi các bước chính xác.
   - Xác định lỗi xảy ra lần nào cũng bị, hay chỉ thỉnh thoảng.
   - Chưa tái hiện được thì thu thêm dữ liệu, không đoán.
3. **Xem thay đổi gần đây**: `git log`, `git diff`, dependency mới, config, khác biệt môi trường.
4. **Khoanh vùng theo từng lớp.** Với hệ thống nhiều lớp (UI → API → service → DB → dịch vụ ngoài), kiểm tra dữ liệu **vào và ra ở mỗi ranh giới** để biết lớp nào bắt đầu sai:
   - Frontend: console, tab network (request gửi đi, response nhận về), state.
   - Backend: log request, log tại ranh giới service/repository, câu query thực tế.
   - Dữ liệu: giá trị trong DB trước và sau thao tác.
5. **Lần ngược dòng dữ liệu**: giá trị sai bắt nguồn từ đâu, ai truyền nó vào. Lần ngược tới điểm bắt đầu sai và sửa ở đó, không sửa ở chỗ triệu chứng lộ ra.

Xong khi: biết **cái gì** sai và **vì sao**.

### Giai đoạn 2: So sánh

1. Tìm đoạn code **tương tự đang chạy đúng** trong codebase.
2. Có pattern hay tài liệu tham chiếu thì đọc hết, không đọc lướt.
3. Liệt kê **mọi** khác biệt giữa bản chạy đúng và bản lỗi, kể cả khác biệt nhỏ. Không loại trước với lý do "cái này không thể ảnh hưởng".
4. Xác định phần này phụ thuộc vào gì: config, biến môi trường, dữ liệu, thứ tự gọi.

Xong khi: thấy được các khác biệt đáng ngờ.

### Giai đoạn 3: Giả thuyết

1. Phát biểu **một** giả thuyết cụ thể: "Lỗi do X, vì Y". Ghi lại.
2. Kiểm chứng bằng **thay đổi nhỏ nhất**, mỗi lần chỉ thay một biến.
3. Đúng thì sang giai đoạn 4. Sai thì đặt giả thuyết **mới**; không chồng thêm bản sửa lên.
4. Không hiểu thì nói "Tôi chưa hiểu X" và tìm hiểu thêm. Không giả vờ hiểu.

### Giai đoạn 4: Sửa

1. **Viết test tái hiện trước**: tối giản nhất có thể, và **phải fail** trước khi sửa.
2. **Sửa một chỗ** ở nguyên nhân gốc. Không tiện tay cải tiến hay refactor kèm theo.
3. **Kiểm chứng** theo skill `verification`:
   - Test tái hiện pass.
   - Red–green: bỏ bản sửa thì test fail.
   - Các test khác không vỡ.
   - Chạy lại các bước tái hiện ban đầu, triệu chứng không còn.
4. Nếu hợp lý, thêm kiểm tra ở các lớp khác (validate đầu vào, assert) để lỗi cùng loại khó tái diễn.

## Luật dừng: 3 lần sửa không thành

Đếm số lần đã sửa thử.
- **Dưới 3 lần**: quay lại giai đoạn 1 với thông tin mới.
- **Từ 3 lần trở lên: DỪNG.** Không thử lần thứ 4.

Dấu hiệu đây là vấn đề kiến trúc chứ không phải bug lẻ:
- Mỗi bản sửa lại lộ ra vấn đề mới ở chỗ khác.
- Muốn sửa thì phải refactor lớn.
- Sửa chỗ này sinh triệu chứng ở chỗ kia.

Khi đó báo cáo những gì đã điều tra, các giả thuyết đã loại, và chuyển cho `leader` quyết định hướng xử lý.

## Không tìm ra nguyên nhân gốc

Phần lớn trường hợp "không có nguyên nhân" thực ra là điều tra chưa đủ. Chỉ kết luận lỗi do môi trường, thời điểm hoặc bên ngoài khi đã đi hết giai đoạn 1–3. Khi đó:

1. Ghi lại đã điều tra những gì.
2. Xử lý phù hợp: retry, timeout, thông báo lỗi rõ ràng.
3. Thêm log/metric để lần sau có dữ liệu điều tra.

Test chập chờn do timing: chờ theo điều kiện (đợi phần tử xuất hiện, đợi request xong), không dùng `sleep` cố định.

## Dùng cho tester: tái hiện & khoanh vùng

Tester làm giai đoạn 1 và 2 để bug report có chất lượng; tester không sửa code.
- Bước tái hiện chính xác và dữ liệu cần dùng.
- Lỗi xảy ra luôn hay thỉnh thoảng; điều kiện để xảy ra.
- **Vùng**: frontend / backend / dữ liệu / chưa rõ, kèm bằng chứng (request/response, log).
- Khác biệt so với trường hợp chạy đúng.

## Dấu hiệu đang làm sai

| Suy nghĩ | Thực tế |
|---|---|
| "Sửa tạm đã, điều tra sau" | Bản sửa đầu tiên quyết định hướng đi. Làm đúng từ đầu. |
| "Thử đổi X xem có được không" | Đó là đoán. Quay lại giai đoạn 1. |
| "Sửa nhiều chỗ cùng lúc cho nhanh" | Không biết cái nào có tác dụng, lại dễ sinh bug mới. |
| "Bỏ qua test, kiểm tay là được" | Bản sửa không có test sẽ không giữ được lâu. |
| "Chắc là do X, sửa luôn" | Thấy triệu chứng chưa phải là hiểu nguyên nhân. |
| "Thử thêm một lần nữa" (đã thử từ 2 lần) | Đến lần thứ 3 thì dừng và xem lại kiến trúc. |
