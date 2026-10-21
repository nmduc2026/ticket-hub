# 0029. Tách service: Notification trước, outbox chuyển sang Debezium

**Trạng thái:** Accepted

## Bối cảnh
Giai đoạn đầu là modular monolith (ADR 0012). Kafka, Avro, Schema Registry, Debezium, Pact, database-per-service chỉ có giá trị khi có ranh giới phân tán; đưa vào sớm thì trả chi phí mà chưa nhận lợi ích. Sprint 8 dùng hệ thống đã chạy để học chuyển sang event-driven phân tán.

## Các phương án
- **Tách Booking hoặc Payment trước:** gần bài toán chính, nhưng nằm trên đường tiền và chống oversell, rủi ro cao nhất ở sprint cuối.
- **Tách Notification trước:** chỉ nhận event, không module nào gọi đồng bộ tới.

## Quyết định
- Lên Kafka (KRaft) + Avro + Schema Registry qua event externalization của Modulith; retry topic / DLT + alert.
- **Tách Notification** thành service đầu tiên với database riêng (database + user riêng trong cụm CNPG hiện có, không dựng cụm mới vì máy staging 32 GB); trace xuyên Kafka.
- Notification chỉ nghe event; dữ liệu cần để gửi đi trong payload, module sở hữu dữ liệu tự phát event theo từng người nhận (`overview.md` §6.5). Lời gọi đồng bộ duy nhất là danh sách Admin qua API nội bộ (client `tickethub-internal`), có Pact HTTP; hợp đồng event do Schema Registry kiểm `BACKWARD`.
- Service đã tách dùng bảng `outbox` + **Debezium** (ADR 0004). Bảng `event_publication` của Modulith và bảng `outbox` theo định dạng Debezium Outbox Event Router khác nhau, nên không dùng chung; service tách ra chuyển hẳn sang cơ chế mới.

## Hệ quả
- (+) Học trọn chu trình tách service mà không đụng đường tiền.
- (+) Các luồng Sprint 1–7 là bộ kiểm tra hồi quy cho việc tách.
- (−) Notification ít logic nên không gặp hết khó khăn khi tách một module có gọi đồng bộ (như Booking ↔ Payment).

Liên quan: `overview.md` §4.3, §8, §17, §22, ADR 0004, 0005, 0012, 0028.
