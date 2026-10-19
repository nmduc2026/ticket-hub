---
name: ticket-hub-springbot-conventions
description: "Quy tắc backend Spring Boot của Ticket Hub: cấu trúc package theo module Spring Modulith, controller / service / repository, DTO, transaction, event giữa module, Flyway, JPA và jOOQ, khoá ghế, cấu hình, job, gọi Stripe, bảo mật và log. Dùng khi viết, sửa hoặc review code trong backend/app hoặc backend/gateway."
---

# Ticket Hub: quy tắc Spring Boot

Đọc kèm `ticket-hub-coding-conventions`. Stack: Java 21, Spring Boot, Spring MVC + virtual threads, Spring Modulith, Maven multi-module (`backend/app`, `backend/gateway`), Postgres, Flyway, JPA + jOOQ, Redis (Redisson), Keycloak (`overview.md` §21.1, ADR 0001, 0012, 0015).

## 1. Cấu trúc một module

Package gốc `com.ndz.backend.<module>`. Package gốc của module là **API công khai**; mọi thứ trong `internal` là riêng của module.

```
com.ndz.backend.booking/
├── BookingApi.java              # interface công khai cho module khác gọi đồng bộ
├── events/                      # record event module phát ra (BookingConfirmed…), @NamedInterface("events")
└── internal/
    ├── domain/                  # entity, value object, máy trạng thái, quy tắc BR
    ├── application/             # service / use case, @Transactional ở đây
    ├── persistence/             # repository JPA, truy vấn jOOQ, SQL khoá ghế
    └── web/                     # controller, request / response record
```

- Module khác chỉ dùng `BookingApi` và `events`. `ApplicationModules.of(...).verify()` + ArchUnit chạy trong test để bắt vi phạm.
- `shared`: ProblemDetail, `BusinessException`, idempotency, tiện ích tiền / thời gian, cấu hình bảo mật chung. Không đưa logic nghiệp vụ của module vào `shared`.

## 2. Web (controller)

- Controller **mỏng**: nhận request, validate, gọi một method application, map response. Không có logic nghiệp vụ, không gọi repository.
- Request / response là `record`; không trả entity ra ngoài. Mapping bằng MapStruct hoặc factory method.
- Path và đặt tên theo `overview.md` §11.1: `/api/v1`, danh từ số nhiều kebab-case; hành động không phải CRUD là `POST .../{action}`.
- Validate bằng Bean Validation (`@Valid`, `@NotNull`, `@Size(max = 6)`…). Lỗi validate → 400 Problem Details.
- `@PreAuthorize` cho role; quyền sở hữu kiểm trong application service.
- OpenAPI sinh bằng springdoc là contract cho frontend: đặt tên schema rõ ràng, khai báo các mã lỗi có thể trả về.

## 3. Application service và transaction

- `@Transactional` đặt ở application service, không đặt ở controller hay repository. Đọc dùng `@Transactional(readOnly = true)`.
- Một use case = một method public, tên theo hành động (`holdSeats`, `startPayment`, `requestRefund`).
- Lỗi nghiệp vụ ném `BusinessException(code, message)`; `@RestControllerAdvice` trong `shared` chuyển thành Problem Details có `code`.
- Không gọi dịch vụ ngoài (Stripe, Keycloak Admin API) bên trong transaction DB đang giữ khoá; ghi trạng thái trước, gọi ngoài sau, có idempotency key.

## 4. Event giữa module

- Event là `record` bất biến trong package `events`, có `eventId` (UUIDv7) và `occurredAt`, để sau này map 1-1 sang envelope Kafka (`overview.md` §8.2).
- Phát bằng `ApplicationEventPublisher` trong cùng transaction với thay đổi dữ liệu (Modulith ghi vào `event_publication`).
- Nghe bằng `@ApplicationModuleListener`. Consumer **idempotent**: ghi `processed_events` trong cùng transaction với xử lý.
- Gọi đồng bộ sang module khác chỉ khi người dùng đang chờ kết quả (vd. Payment → `BookingApi.startPayment`), ADR 0018.

## 5. Persistence

- Mỗi module một schema Postgres; Flyway migration của module nằm trong `db/migration/<module>/`, tên `V<yyyyMMddHHmm>__<module>_<mô_tả>.sql`.
- Migration không sửa sau khi đã merge; đổi schema theo **expand → migrate → contract** (`overview.md` §18). Migration có `DROP` / đổi kiểu cột gắn `[USER-REVIEW]`.
- Khoá chính UUIDv7 sinh ở app. Bảng cập nhật đồng thời có `@Version`.
- `spring.jpa.open-in-view=false`. Quan hệ mặc định `LAZY`; tránh N+1 (fetch join / entity graph / jOOQ cho truy vấn đọc phức tạp).
- jOOQ dùng cho báo cáo và truy vấn đọc nhiều bảng **trong cùng module**.
- SQL giữ ghế theo `overview.md` §10: `SELECT ... ORDER BY seat_id FOR UPDATE` rồi `UPDATE ... WHERE status = 'AVAILABLE'`, so số dòng; deadlock `40P01` thử lại 1 lần rồi trả `SEAT_UNAVAILABLE`.
- Ràng buộc bất biến đặt ở DB (unique index có `WHERE`, `CHECK`), không chỉ ở code.

## 6. Domain

- Tiền: value object `Money(long amountMinor, Currency currency)`; phép tính phí ở một chỗ.
- Thời gian: `Instant` + `Clock` được inject.
- Máy trạng thái (`overview.md` §7.7) cài trong entity / domain service: method chuyển trạng thái kiểm trạng thái hiện tại, chuyển không hợp lệ ném lỗi hoặc bỏ qua có log, không có setter `status` public.

## 7. Cấu hình

- `@ConfigurationProperties` dạng `record`, prefix `tickethub.<module>`, có mặc định theo BR:

```yaml
tickethub:
  booking:
    hold-duration: 10m
    payment-start-min-remaining: 90s
    payment-grace: 5m
    max-seats-per-booking: 6
```

- Secret chỉ đến từ biến môi trường / K8s Secret / Vault, không nằm trong `application.yml` commit lên Git.

## 8. Job định kỳ

`@Scheduled` + **ShedLock** cho mọi job (`ExpireBookings`, đối soát, chuyển trạng thái sự kiện, dọn idempotency key). Job xử lý theo lô, idempotent, chạy lại không gây sai.

## 9. Gọi dịch vụ ngoài (Stripe, Keycloak Admin API)

- Bọc trong adapter thuộc `internal` của module (vd. `payment.internal.stripe.StripeGateway`); domain không phụ thuộc SDK.
- Resilience4j theo `overview.md` §15.1: timeout, retry **chỉ khi có idempotency key**, circuit breaker, bulkhead.
- Mọi lệnh tạo / hoàn tiền có idempotency key theo §7.2, §7.4 (`booking-{id}`, `refund-{refundId}`).
- Webhook: verify chữ ký trên raw body, dedupe theo `stripe_event_id`, xử lý theo máy trạng thái.

## 10. Bảo mật

- `app` là Resource Server: kiểm chữ ký, `iss`, `aud=tickethub-api`, role từ `realm_access.roles`, chỉ map `CUSTOMER` / `ORGANIZER` / `ADMIN`.
- `gateway` theo đúng bảng "Yêu cầu cấu hình bắt buộc" ở `overview.md` §13.1. Mọi thay đổi cấu hình bảo mật gắn `[USER-REVIEW]`.
- Lấy người dùng hiện tại qua một helper trong `shared` (`CurrentUser`), không đọc `SecurityContextHolder` rải rác.

## 11. Log và quan sát

- SLF4J, log JSON có `traceId`; thêm `bookingId`, `eventId`, `userId` qua MDC / structured argument khi có.
- Mức log: `INFO` cho chuyển trạng thái nghiệp vụ, `WARN` cho bù trừ, `ERROR` cho lỗi cần người xử lý. Không log trong vòng lặp nóng.
- Metric nghiệp vụ bằng Micrometer (vé bán, booking hết hạn, thanh toán lỗi) với tên `tickethub.<module>.<metric>`.

## 12. Virtual threads

Không bọc I/O trong `synchronized` (gây pinning); dùng `ReentrantLock` nếu cần khoá trong JVM. Giới hạn đồng thời thật nằm ở pool DB và bulkhead, không ở thread.

## 13. Quy trình làm task backend

1. Đọc hạng mục trong plan, đúng các § overview và ADR được nêu (không đọc cả `overview.md`), rồi đọc code xung quanh. Code mới phải trông như cùng một người viết.
2. Chỗ mơ hồ ảnh hưởng tới dữ liệu hoặc API: dừng lại hỏi người dùng, không tự đoán. Thay đổi lớn (schema, transaction, luồng nhiều module) thì nêu phương án ngắn trước khi code.
3. Làm từng bước nhỏ, mỗi bước build được. Viết test cùng lúc theo `ticket-hub-testcase-rule`; sửa bug theo `systematic-debugging`.
4. Chạy `./mvnw verify` (lệnh đầy đủ trong `CLAUDE.md`) tới khi xanh. Không skip hay tắt test.
5. Đổi API thì kiểm OpenAPI sinh ra đúng, và nhắc phần frontend chạy lại Orval.
6. Tự review diff theo phần B của `code-review`; lập bảng truy vết theo `verification`.
7. Báo cáo bàn giao:

```
## Tóm tắt thay đổi
## File đã thay đổi (mô tả ngắn từng file)
## Bảng truy vết AC / BR → bằng chứng → kết quả
## Lệnh đã chạy → kết quả
## Thay đổi API (endpoint, mã lỗi) / Migration (tên, cách chạy khi deploy)
## Rủi ro, nợ kỹ thuật, việc còn lại
## Thao tác cần báo trước (commit, migration ngoài local...)
```
