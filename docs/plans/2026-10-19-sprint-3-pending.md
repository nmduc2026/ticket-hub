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

## Việc kỹ thuật còn lại

| # | Việc | Ghi chú |
|---|---|---|
| 1 | `spotless:check` đỏ ở `PublicEventController.java` | Có từ Sprint 2 (CRLF trên Windows, xem pending Sprint 2 mục 11); file của hạng mục 1 sạch |
| 2 | Thêm `STRIPE_*` vào `infra/docker-compose/.env.example` và service stripe-mock vào compose | Chưa làm; cần khi chạy integration test |
| 3 | Retry / circuit breaker / bulkhead cho `StripeGateway` | Sprint 4 (Resilience4j); hiện chỉ có timeout 3 giây, SDK không tự retry |
