# 0004. Outbox: Spring Modulith trước, Debezium khi tách service

**Trạng thái:** Accepted

## Bối cảnh
Ghi dữ liệu nghiệp vụ rồi gửi event là hai bước; nếu bước gửi thất bại sau khi DB đã commit thì event mất ("lưu xong mà quên gửi"), saga đặt vé dừng giữa chừng. Cần ghi event trong cùng transaction với dữ liệu (P3).

Hai cơ chế outbox không dùng chung một bảng: Spring Modulith lưu event ở bảng `event_publication` của nó, còn Debezium Outbox Event Router cần bảng `outbox (id, aggregate_type, aggregate_id, event_type, payload, created_at)` theo định dạng riêng.

## Các phương án
- **Polling publisher:** job quét bảng outbox định kỳ rồi đẩy lên Kafka. Đơn giản, nhưng có độ trễ theo chu kỳ quét và tạo tải đọc liên tục lên DB.
- **Spring Modulith event publication:** event ghi vào `event_publication` cùng transaction, listener trong process xử lý, event chưa xong được gửi lại khi khởi động; lên Kafka bằng event externalization.
- **Debezium (CDC):** đọc WAL của Postgres, đẩy bảng `outbox` sang Kafka; chuẩn công nghiệp khi có nhiều service.

## Quyết định
- Giai đoạn modular monolith: **Spring Modulith** (`event_publication`); khi có Kafka (Sprint 8) dùng event externalization.
- Khi tách service: mỗi service có bảng `outbox` riêng, **Debezium** đẩy sang Kafka. Chuyển hẳn, không chạy song song hai cơ chế trên một bảng.

## Hệ quả
- (+) Ngày đầu không cần Kafka hay Debezium; outbox có sẵn nhờ Modulith.
- (+) Học được cả hai cách, và lý do mỗi cách hợp với giai đoạn nào.
- (−) Service tách ra phải đổi cơ chế outbox và thêm Debezium Connect vào hạ tầng.

Liên quan: `overview.md` §4.2 (P3), §8.3, §19.2, ADR 0012, 0029.
