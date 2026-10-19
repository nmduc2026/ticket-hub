# 0006. Tìm kiếm sự kiện: Elasticsearch

**Trạng thái:** Accepted

## Bối cảnh
Khách tìm sự kiện theo từ khoá (tên nghệ sĩ, tên show, có hoặc không dấu), lọc theo thành phố, ngày, danh mục. Dữ liệu sự kiện nằm ở Catalog.

## Các phương án
- **Postgres full-text:** không thêm hạ tầng, nhưng xếp hạng kết quả và tìm tiếng Việt không dấu hạn chế.
- **Elasticsearch:** full-text mạnh, analyzer linh hoạt; dùng chung kiến thức và hạ tầng với ELK (ADR 0010).

## Quyết định
**Elasticsearch** làm projection tìm kiếm của Catalog, cập nhật từ `catalog.events.v1`. Bản ghi sự kiện lưu sẵn tên tỉnh để lọc "Thành phố" không phải join. Postgres vẫn là nguồn sự thật; index dựng lại được bằng cách replay event.

Trước khi có Elasticsearch (đưa vào Sprint 8), tìm kiếm chạy bằng truy vấn Postgres đơn giản; truy vấn này giữ lại làm fallback khi Elasticsearch chết.

## Hệ quả
- (+) Tìm kiếm tốt, học thêm một hệ thống phổ biến.
- (+) Elasticsearch chết thì chỉ mất full-text, đặt vé không ảnh hưởng.
- (−) Thêm một projection phải giữ đồng bộ; tốn RAM (profile `search` ~2.5 GB).

Liên quan: `overview.md` §5, §6.1, §15.2, §18, ADR 0010.
