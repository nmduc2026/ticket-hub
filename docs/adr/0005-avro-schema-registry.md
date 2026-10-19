# 0005. Định dạng event: Avro + Schema Registry

**Trạng thái:** Accepted

## Bối cảnh
Khi lên Kafka (Sprint 8) và tách service, producer và consumer deploy độc lập. Đổi cấu trúc event mà không có kiểm soát thì consumer cũ đọc lỗi, message vào DLT hàng loạt.

## Các phương án
- **JSON không schema:** dễ đọc, dễ debug, nhưng không có gì chặn producer phát event sai cấu trúc hay đổi trường phá vỡ consumer.
- **Avro + Schema Registry:** schema khai báo trước, registry kiểm tính tương thích khi đăng ký phiên bản mới; message nhỏ hơn JSON.

## Quyết định
**Avro + Schema Registry**, chế độ tương thích `BACKWARD`. Schema lưu trong `backend/contracts`. Envelope chung (`eventId`, `eventType`, `schemaVersion`, `aggregateId`, `occurredAt`, `traceId`, `payload`) theo `overview.md` §8.2.

Giai đoạn Modulith in-process, event là Java record mang sẵn `eventId` (UUIDv7) và `occurredAt` để map 1-1 sang envelope khi lên Kafka (ADR 0018).

## Hệ quả
- (+) Đổi schema phá vỡ consumer bị chặn ngay lúc đăng ký, không đợi tới production.
- (+) Học được tiến hoá schema, một chủ đề hay gặp khi làm Kafka thật.
- (−) Thêm Schema Registry vào hạ tầng; đọc message thô khó hơn JSON (cần Kafka UI có hỗ trợ Avro).

Liên quan: `overview.md` §8.2, §21.1, ADR 0004, 0018, 0029.
