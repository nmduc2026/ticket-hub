# Việc còn treo: Sprint 3

Ghi những việc chưa xong hoặc cần người dùng làm. Xoá dòng khi xong. Sprint 3 được code khi chưa bật Docker: test cần Docker được viết nhưng chưa chạy.

## Cần người dùng

| # | Việc | Vì sao treo | Cách làm |
|---|---|---|---|
| 1 | Tạo tài khoản Stripe test mode (Singapore), đặt `STRIPE_SECRET_KEY` và `STRIPE_WEBHOOK_SECRET` vào `.env` | Câu hỏi mở BLOCKING trước việc 2; Claude không tạo tài khoản thay | Dashboard Stripe → Developers → API keys; `stripe listen` cho webhook secret |
| 2 | Bật Docker rồi chạy `./mvnw verify` | Các test Testcontainers viết ở Sprint 3 chưa chạy lần nào (liệt kê ở bảng dưới) | Xong thì xoá dòng ở bảng "Test viết nhưng chưa chạy" |
| 3 | Rebase `feature/sprint-3` lên `main` khi PR Sprint 2 được squash merge | Nhánh Sprint 3 tách từ `feature/sprint-2` | `git rebase --onto main <commit-cuối-sprint-2> feature/sprint-3` |

## Test viết nhưng chưa chạy (cần Docker)

| Hạng mục | Test | Cần |
|---|---|---|
| 1 | `MigrationTests.paymentSchemaHasItsTables` | Postgres (Testcontainers) |
| 1 | Gọi `StripeGateway` trên stripe-mock: PI chỉ tạo 1 lần với cùng idempotency key | stripe-mock (chưa có trong compose) |

## Refactor cấu trúc BE (chia theo tầng package): chạy lại khi có Docker

Đã đổi cấu trúc `account`, `catalog`, `booking`, `payment`, `shared` theo `ticket-hub-springbot-conventions` mục 1; chỉ chạy được biên dịch, `ModularityTests`, `ArchitectureTests` và các unit test không cần DB. Mọi test dưới đây **chưa chạy lại sau refactor**. Chạy `./mvnw verify`; nếu đỏ, nghi ngờ trước hết các điểm ghi ở cột "Điểm dễ vỡ".

| Phase | Test cần chạy lại | Điểm dễ vỡ |
|---|---|---|
| shared | `GlobalExceptionHandlerTests`, `ResourceServerSecurityTests`, `IdempotencyKeyTests`, `OpenApiContractTests`, `JsonLoggingTests` | Mã lỗi chuyển sang enum `ErrorCode` (trạng thái HTTP lấy từ enum, bỏ tham số `HttpStatus` ở từng chỗ ném); `ProblemWriter.write` đổi chữ ký; probe test đổi mã `HOLD_EXPIRING` thành `MAX_SEATS_EXCEEDED` |
| account | `MeEndpointTests` | `UserProfileRepository` nay có phần SQL qua fragment `UserProfileRepositoryImpl` (Spring Data ghép theo tên); `MeResponse` tách ra `dto/response` |
| catalog | `OrganizerEventApiTests`, `EventPublishApiTests`, `EventBannerApiTests`, `CatalogSchemaTests` | Service trả DTO qua `EventMapper` (trước đây controller dựng response); `ObjectMapper` vào `VenueMapper` |
| booking | `BookingHoldTests`, `BookingExpiryAndCancelTests`, `BookingProjectionTests`, `PublicEventApiTests`, 50 luồng tranh 1 ghế | Hai class handler event đổi thành listener mỏng gọi `EventProjectionService` (cùng tên consumer `booking.event-published`, `booking.event-snapshots`); `@ApplicationModuleListener` vẫn mở transaction ở listener |
| payment | `StripeGatewayTests` (đã chạy xanh), `MigrationTests` | `StripeConfiguration` chuyển sang `payment/config` |
| Hợp đồng API | `OpenApiContractTests` | Tên schema giữ nguyên bằng `@Schema(name)`; nếu `openapi.json` khác thì commit bản mới rồi `npm run api:generate` |

## Việc kỹ thuật còn lại

| # | Việc | Ghi chú |
|---|---|---|
| 1 | `spotless:check` đỏ ở `PublicEventController.java` | Có từ Sprint 2 (CRLF trên Windows, xem pending Sprint 2 mục 11); file của hạng mục 1 sạch |
| 2 | Thêm `STRIPE_*` vào `infra/docker-compose/.env.example` và service stripe-mock vào compose | Chưa làm; cần khi chạy integration test |
| 4 | `booking` vẫn dùng JdbcTemplate (chưa chuyển sang JPA) | SQL giữ ghế (`FOR UPDATE`, `UPDATE ... WHERE status = 'AVAILABLE'`, `RETURNING`, upsert `ON CONFLICT ... WHERE`) là chỗ đặc thù. Chuyển sang entity + Spring Data (giữ nguyên SQL bằng native query) nên làm sau khi bật Docker, có test 50 luồng và truy vấn ghế trùng làm lưới an toàn |
| 5 | `shared/idempotency`, `shared/storage` giữ dạng gói theo tính năng, chưa chia `config` / `util` | Chia nếu bạn muốn đồng nhất |
| 3 | Retry / circuit breaker / bulkhead cho `StripeGateway` | Sprint 4 (Resilience4j); hiện chỉ có timeout 3 giây, SDK không tự retry |
