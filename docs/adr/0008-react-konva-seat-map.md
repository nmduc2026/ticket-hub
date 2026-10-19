# 0008. Sơ đồ ghế: react-konva (canvas)

**Trạng thái:** Accepted

## Bối cảnh
Sơ đồ ghế của sân lớn có hàng nghìn tới hàng chục nghìn ghế; cần zoom / pan, màu theo hạng vé và trạng thái, cập nhật realtime khi ghế bị giữ / bán. Admin cần editor để vẽ sơ đồ.

## Các phương án
- **SVG (DOM):** dễ làm, dễ style và accessibility, nhưng mỗi ghế là một node DOM; vài nghìn node trở lên thì zoom / pan và cập nhật trạng thái bị giật.
- **Canvas qua react-konva:** vẽ trên canvas, hiệu năng tốt với số lượng lớn; vẫn viết theo kiểu component React.

## Quyết định
**react-konva.** Một feature `seat-map` dùng chung cho chọn ghế (customer) và editor (admin). Trạng thái ghế cập nhật qua SSE (ADR 0011).

## Hệ quả
- (+) Mượt với hàng nghìn ghế, hợp với flash sale khi trạng thái đổi liên tục.
- (−) Canvas không có DOM cho từng ghế: accessibility (bàn phím, screen reader) và test component phải làm thêm.
- (−) Editor sơ đồ trên canvas tốn công FE; dữ liệu ban đầu dùng seed và import Excel.

Liên quan: `overview.md` §14.3, §14.4, ADR 0011.
