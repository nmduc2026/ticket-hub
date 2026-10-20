# 0030. Editor sơ đồ ghế: kéo-thả theo khu, ghế sinh từ tham số

**Trạng thái:** Accepted

## Bối cảnh
Mỗi địa điểm có bố cục riêng: sân khấu ở giữa hoặc ở cạnh, khu ghế xoay hoặc xếp hình cung, có lối đi và cột che. Admin cần dựng sơ đồ khớp thực tế mà không phải nhập toạ độ từng ghế. Dữ liệu hiện có `seats.x, y` và `sections.shape_json` nhưng chưa có chỗ lưu sân khấu, lối đi, nhãn. Ràng buộc: chi phí ~0$, dùng react-konva (ADR 0008), xong trong Sprint 6, sơ đồ đã publish thì bất biến, import Excel vẫn dùng được, màn chọn ghế của khách mượt với ~20.000 ghế.

## Các phương án
- **Form + lưới:** mỗi khu là lưới hàng × ghế, vị trí và góc nhập bằng ô số, có khung xem trước. Ít code nhưng đặt bố cục bằng cách mò số; sân khấu chỉ là một khối.
- **Kéo-thả theo khu:** kéo, xoay khu ghế và các phần tử không bán (sân khấu, lối đi, nhãn) trên canvas; ghế sinh tự động từ tham số khu; chỉnh từng ghế chỉ ở mức ẩn ghế.
- **Tự do từng ghế (kiểu seats.io):** vẽ hình khu tự do, kéo từng ghế. Linh hoạt nhất nhưng phức tạp, dễ trễ sprint.
- **Dịch vụ ngoài (seats.io):** trả phí, loại vì vi phạm chi phí ~0$.

## Quyết định
**Kéo-thả theo khu.**
- Khu ghế `GRID` lưu tham số trong `sections.shape_json`: `x, y, rotation, rows, seatsPerRow, curve, rowGap, seatGap, numbering, hiddenSeats`. Khu `IMPORTED` (từ Excel) giữ toạ độ ghế của file, chỉ di chuyển / xoay cả khu.
- Phần tử không bán lưu ở bảng mới `seat_map_elements` (sân khấu, lối đi, nhãn, vùng đứng).
- Một hàm sinh ghế thuần (`generateSeats(shape)`) dùng chung cho editor, màn chọn ghế và backend khi lưu. Khi lưu sơ đồ, backend sinh lại `seats.x, y` từ tham số và ghi vào DB, nên màn khách chỉ đọc toạ độ đã lưu.
- Thao tác editor: chọn, kéo, xoay theo bước 15°, snap lưới, phím mũi tên dịch 1 ô, nhân bản khu, undo / redo, xem trước như khách thấy.

## Hệ quả
- (+) Admin dựng được bố cục thật (sân khấu bất kỳ, khu xoay, hàng cong, lối đi) mà không nhập toạ độ ghế.
- (+) Editor và màn khách dùng chung component và hàm sinh ghế, không lệch vị trí.
- (+) Dữ liệu là tham số, nâng lên vẽ tự do sau này không phải đổi lại khu cũ.
- (−) Không vẽ được hình khu bất kỳ, không kéo từng ghế riêng; lối đi tạo bằng ẩn ghế.
- (−) Thêm toán xoay / cung tròn: làm spike 1 ngày cho `generateSeats` trước khi làm editor; quá 1 ngày chưa ổn thì lùi về form + lưới.
- (−) Sơ đồ lớn cần cache theo khu trên konva và tắt hit-test từng ghế khi đang kéo khu.

Liên quan: `overview.md` §6.1, §14.4, ADR 0008.
