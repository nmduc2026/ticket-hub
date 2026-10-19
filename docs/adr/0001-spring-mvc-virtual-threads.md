# 0001. Spring MVC + Virtual Threads

**Trạng thái:** Accepted

## Bối cảnh
Hệ thống phải giữ hàng nghìn request và kết nối SSE đồng thời lúc flash sale, nhưng code nghiệp vụ (giữ ghế, saga thanh toán) cần dễ đọc, dễ debug.

## Các phương án
- **Spring MVC + Virtual Threads (Java 21):** code tuần tự quen thuộc; mỗi request một virtual thread nên chờ I/O không tốn platform thread.
- **Spring WebFlux (reactive):** chịu tải đồng thời tốt, nhưng phải đổi cả hệ sinh thái sang reactive (R2DBC, client reactive), khó debug, khó học song song với các phần khác.

## Quyết định
Spring MVC, bật `spring.threads.virtual.enabled=true` cho `app` và `gateway`.

## Hệ quả
- (+) Dùng được JPA, jOOQ, JDBC, Testcontainers như bình thường; stack trace dễ đọc.
- (+) SSE (`SseEmitter`) giữ nhiều kết nối mà không cạn thread.
- (−) Tránh `synchronized` quanh I/O (gây pinning virtual thread); dùng `ReentrantLock` khi cần.
- (−) Pool kết nối DB (HikariCP) vẫn là giới hạn thật, phải cấu hình kích thước phù hợp.

Liên quan: `overview.md` §12, §21.1.
