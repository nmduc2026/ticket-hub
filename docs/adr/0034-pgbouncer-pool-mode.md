# 0034. Chế độ pool của PgBouncer (CloudNativePG Pooler)

**Trạng thái:** Proposed (chờ đo trên staging, Sprint 5 hạng mục 12)

## Bối cảnh
App chạy Java 21 virtual threads, HikariCP giữ pool kết nối tới Postgres. Dưới tải (500 user) và khi chạy nhiều pod, số kết nối thật tới Postgres tăng theo số pod nhân kích thước pool. `Pooler` của CloudNativePG dựng PgBouncer trước cụm để chặn số kết nối thật (`default_pool_size`). Câu hỏi: chế độ `transaction` (gom tốt) hay `session` (an toàn)?

Những thứ trong app có thể vướng chế độ `transaction`:
- Prepared statement của pgjdbc (tự chuyển sang server-side prepare sau 5 lần chạy cùng câu lệnh) và của Hibernate.
- Khoá advisory theo phiên của Flyway; ShedLock dùng bảng `shedlock` (UPDATE trong transaction) nên không vướng.
- `SELECT ... FOR UPDATE` giữ ghế nằm gọn trong một transaction nên không vướng.

## Các phương án

| Phương án | Ưu | Nhược |
|---|---|---|
| **A. `transaction` + `max_prepared_statements`** | Gom kết nối tốt nhất: vài chục kết nối thật phục vụ hàng trăm kết nối client. PgBouncer >= 1.21 theo dõi prepared statement ở mức giao thức nên pgjdbc/Hibernate chạy bình thường | Cần PgBouncer >= 1.21 (image của CNPG hiện có); khoá advisory theo phiên không dùng được qua pooler → Flyway nối thẳng Postgres; không dùng `SET` theo phiên, `LISTEN/NOTIFY` |
| **B. `session`** | Không đổi hành vi nào của app; khoá advisory, `SET` theo phiên chạy như cũ | Mỗi client giữ một kết nối thật suốt phiên nên gần như không gom được gì khi pool HikariCP đã nhỏ; lợi ích chỉ là chặn trần khi nhiều pod |
| **C. Không dùng Pooler** | Ít thành phần nhất | Số kết nối thật tăng tuyến tính theo số pod; `max_connections` 200 là trần cứng |

## Đề xuất
**A**, kèm hai quy tắc: (1) Flyway nối thẳng `tickethub-pg-rw` (`SPRING_FLYWAY_URL`); (2) app không dùng `SET`/`LISTEN` theo phiên (hiện không có). Pool HikariCP của app giảm còn 10. Quay về **B** nếu khi chạy Load xuất hiện lỗi `prepared statement ... does not exist` hoặc hành vi lệch giữa kết nối.

## Cần kiểm trên staging
- Phiên bản PgBouncer thật trong image Pooler (cần >= 1.21 để `max_prepared_statements` có tác dụng).
- Chạy Load: số dòng `pg_stat_activity` của database `tickethub` giảm so với nối thẳng, không có lỗi prepared statement, p95 không xấu đi.

## Hệ quả
- `infra/k8s-platform/postgres/pooler.yaml`, NetworkPolicy `allow-app-to-pooler`.
- `infra/helm/app/values.yaml`: `DB_URL` trỏ `tickethub-pg-pooler-rw`; Flyway có URL và mật khẩu riêng.

Liên quan: `overview.md` §17 (PgBouncer), ADR 0013, ADR 0021.
