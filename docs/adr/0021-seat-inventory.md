# 0021. Tạo tồn kho ghế khi publish

**Trạng thái:** Accepted

## Bối cảnh
Booking cần `seat_inventory` (từng ghế kèm hạng vé, giá, nhãn) để giữ ghế, nhưng dữ liệu gốc nằm ở Catalog (`seats`, `sections`, `tier_sections`, `ticket_tiers`).
- Đưa danh sách ghế vào `EventPublished`: 10.000 ghế × ~100 byte ≈ 1 MB, chạm giới hạn message mặc định của Kafka.
- Không có bước xác nhận thì có khoảng thời gian sự kiện hiện "đang bán" mà chưa có ghế.
- Admin sửa / xoá ghế trên sơ đồ đang được sự kiện bán → tồn kho và vé đã phát lệch với sơ đồ.
- `tier_sections` thiếu `event_id` → một khu ghế gán được vào 2 hạng vé của cùng sự kiện.

## Các phương án
- **A. `EventPublished` chỉ mang metadata, Booking kéo danh sách ghế qua API nội bộ của Catalog.**
- **B. Chia danh sách thành nhiều event `SeatInventoryChunk`:** không vướng giới hạn message, nhưng phải ghép lại và kiểm đã nhận đủ.
- **C. Giai đoạn monolith: Booking gọi interface công khai của module catalog trong cùng process.**

## Quyết định
**C, rồi A khi tách service.**
- Publish → `EventPublished` (chỉ metadata) → Booking lấy danh sách ghế đang bán qua interface công khai của catalog → tạo `seat_inventory` idempotent (`ON CONFLICT DO NOTHING`) → `SeatInventoryReady` → Catalog đặt `inventory_ready = true`. Sự kiện chỉ chuyển `ON_SALE` khi `inventory_ready`.
- Chỉ ghế thuộc khu có hạng vé mới được tạo tồn kho; khu không gán hạng vé hiển thị màu xám.
- `tier_sections` có `UNIQUE (event_id, section_id)`.
- Sơ đồ ghế đã được sự kiện publish dùng thì **bất biến** (`locked`); sửa thì tạo bản sao với `version` mới.
- Khi tách service: thay cài đặt của interface bằng lời gọi API nội bộ (client `tickethub-internal`), code của Booking giữ nguyên.

## Hệ quả
- (+) Không có message lớn; tồn kho luôn có trước khi mở bán.
- (+) Vé đã phát luôn khớp với sơ đồ.
- (−) Publish chưa xong ngay: phải chờ Booking tạo tồn kho.
- (−) Sửa sơ đồ sau khi đã dùng phải tạo phiên bản mới.

Liên quan: BR-14; `overview.md` §6.1, §6.2, §7.7, §7.9, ADR 0018.
