# 0012. Modular monolith trước, tách service sau

**Trạng thái:** Accepted

## Bối cảnh
Thiết kế đích có 7 service, mỗi service một database, giao tiếp qua Kafka. Làm phân tán ngay từ đầu kéo theo rất nhiều việc (Kafka, schema, tracing xuyên service, deploy nhiều thành phần) trước khi có một luồng bán vé chạy được.

## Các phương án
- **Modular monolith (Spring Modulith):** một ứng dụng `app`, mỗi module nghiệp vụ một package có ranh giới được kiểm tự động; event giữa module đi in-process. Tách thành service khi đã có hệ thống chạy.
- **Microservices ngay từ đầu:** đúng hình thái đích, nhưng chậm có sản phẩm chạy được và khó debug khi chưa có observability.

## Quyết định
Modular monolith. Mỗi module (`account`, `catalog`, `booking`, `payment`, `ticket`, `notification`, `shared`) chỉ lộ interface công khai; Spring Modulith `verify()` + ArchUnit chặn truy cập vào bảng hay class nội bộ của module khác. Mỗi module một schema Postgres. Sprint 8 tách service đầu tiên (ADR 0029).

## Hệ quả
- (+) Có luồng end-to-end sớm; refactor ranh giới rẻ khi còn chung một codebase.
- (+) Ranh giới module trùng ranh giới service, tách sau không phải viết lại.
- (−) Phải kỷ luật ranh giới ngay từ đầu (test kiến trúc chạy trong CI).
- (−) Một số kỹ thuật phân tán (Kafka, Debezium, KEDA scale-to-zero) chỉ học được ở Sprint 7–8.

Liên quan: `overview.md` §4.2 (P7), §4.3, §22, ADR 0015, 0018.
