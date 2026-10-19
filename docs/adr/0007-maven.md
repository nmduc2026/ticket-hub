# 0007. Maven làm build tool

**Trạng thái:** Accepted

## Bối cảnh
Backend Java nhiều module (`app`, `gateway`, sau này `platform/*`, `*-service`), cần quản lý version tập trung và chạy ổn định trên CI.

## Các phương án
- **Maven:** phổ biến trong doanh nghiệp, cấu hình khai báo, multi-module + BOM rõ ràng; người dùng đã quen.
- **Gradle:** build nhanh hơn nhờ cache, linh hoạt, nhưng script là code nên dễ phức tạp.

## Quyết định
Maven multi-module, có Maven Wrapper (`mvnw`). `backend/pom.xml` là parent, quản lý version bằng `dependencyManagement` (Spring Boot BOM, Spring Modulith BOM, Testcontainers BOM…).

## Hệ quả
- (+) Cấu trúc dễ đọc, dễ review; CI dùng lại cache `~/.m2`.
- (−) Build chậm hơn Gradle; bù lại bằng cache trên CI và chạy test song song.

Liên quan: `overview.md` §21.1, §22, ADR 0015.
