# Báo cáo verify Sprint 4

Nhánh `feature/sprint-4`. Chạy ngày 21/10/2026 trên máy dev (Windows 11), Docker bật, không có cụm k3d, không có Stripe/Telegram/Sentry thật. Plan: `docs/plans/2026-10-19-sprint-4-plan.md`. Việc treo: `docs/plans/2026-10-19-sprint-4-pending.md`.

## Lệnh đã chạy

| Lệnh | Kết quả |
|---|---|
| `cd backend && ./mvnw verify -Dspotless.check.skip=true` (Testcontainers) | BUILD SUCCESS: `app` 328 test, `gateway` 9 test, 0 lỗi, 0 bỏ qua; `git status` sạch sau khi chạy (`openapi.json` không lệch) |
| `cd frontend && npx tsc -b` / `npx oxlint src` | Không lỗi |
| `cd frontend && npx vitest run` | 24 file, 131 test xanh |
| `cd frontend && npx playwright test mock` (1280px và 390px) | 19 xanh, 1 bỏ qua có chủ đích |
| PyYAML đọc mọi file `infra/argocd`, `infra/k8s-platform/observability`, `postgres/cluster.yaml` | Hợp lệ cú pháp |
| `helm lint` chart `app`, `gateway`; `helm template app` | Sạch; có `OTLP_TRACES_*` |
| gitleaks (pre-commit) mỗi commit | Không có secret |

Không chạy: `spotless:check` (xem pending mục 15), `promtool`, `kubectl --dry-run`.

## Truy vết AC

| AC | Bằng chứng | Kết quả |
|---|---|---|
| AC-1 (BR-07) | `RefundFlowTests.customerRefundSendsNinetyFiveThousandToStripeAndEndsRefunded` (WireMock), email xác nhận hoàn | Đạt trên WireMock; **chưa chạy Stripe thật** |
| AC-2 | `BookingRefundTests`: `exactlyFortyEightHours…`, `oneSecondShortOfFortyEightHoursIsRefused`, `onlyAConfirmedBookingCanBeRefunded`, `someoneElsesBookingIsNotFound`, `sameIdempotencyKeyReturnsTheSameResponse`, `twoDifferentKeysRaceExactlyOnceWins` | Đạt |
| AC-3 (BR-09) | `ticketsAreRevokedAtOnceAndSeatsFreedOnlyAfterEveryTicketIsRevoked`, `seatsStaySoldUntilTheLastTicketIsRevoked`, `refundRequestedBeforeTicketsAreIssuedStillRevokesThem`, `aRefundedSeatCanBeHeldByAnotherCustomer` | Đạt |
| AC-4 | `RefundFlowTests.aFailedRefundLeavesTheBookingRefundFailedWithTicketsRevoked`, `adminRetryCreatesANewRefundAttemptWithANewKey`, `adminRetryOnlyWorksOnRefundFailedBookings`, `adminListsRefundFailedBookingsAndOnlyAdminMay`; alert `RefundFailedBookings` | Đạt trên WireMock; thẻ `4000…5126` thật và alert tới Telegram **chưa chạy** |
| AC-5 | `TicketIssuanceCompensationTests.aSeatThatAlreadyHasALiveTicketIsRefundedInFull`, `aDatabaseFailureIsRetriedAndNeverRefunded`; `RefundFlowTests.ticketIssuanceFailureRefundsTheWholeAmountIncludingTheFee` | Đạt; alert mức cao tới Telegram chưa chạy |
| AC-6 | `StripeWebhookApiTests.processingAfterSucceededIsIgnored`, `failedAfterSucceededIsIgnored`, `succeededForACanceledPaymentIsIgnored` | Đạt |
| AC-7 (BR-09) | `CheckInApiTests`: `aValidTicketChecksInOnce…`, `aRevokedTicketIsRefused`, `simultaneousScansOfOneTicketLetExactlyOneIn`, `anotherOrganizerCannotCheckInOrProbeTheTicket`, `forgedTokensAreRefused`; FE `checkin.mock.spec.ts` (QR giả không gọi server, mất mạng báo lỗi) | Đạt trên mock; **quét camera điện thoại thật chưa chạy** |
| AC-8 | `CheckInApiTests.ticketsSignedBeforeAndAfterKeyRotationBothCheckIn`, `keySetListsActiveAndRetiredKeysWithoutPrivateParts`; runbook `rotate-ticket-signing-key.md` | Đạt |
| AC-9 | `StripeResilienceTests`: `aSlowStripeCallStopsAtThreeSeconds`, `consecutiveFailuresOpenTheCircuitBreaker`, `callsWithoutAnIdempotencyKeyAreNeverRetried`, `theBreakerRecoversThroughHalfOpen`, `callsBeyondTheBulkheadLimitAreRejectedImmediately` | Đạt |
| AC-10 (C7) | Manifest CNPG + `runbooks/postgres-pitr.md` | **Chưa chạy** (không có cụm) |
| AC-11 (C1) | `runbooks/postgres-failover.md` | **Chưa chạy** |
| AC-12 | Rule, route Alertmanager, runbook viết xong (`infra/k8s-platform/observability/`, `docs/runbooks/`); cert 1 giờ `cert-drill.yaml` | **Chưa chạy**: cần cụm và Telegram bot |
| AC-13 | `JsonLoggingTests.logLineIsJsonWithTraceId`, `appContinuesTraceFromGatewayTraceparent` (app nối `traceparent` vào log); FE `monitoring.test.ts` (lỗi JS gọi Sentry khi có DSN) | Phần log đạt; **trace trong Jaeger và lỗi trong Sentry chưa thấy** |
| AC-14 | `InAppNotificationTests.ticketIssuedCreatesExactlyOneNotificationEvenWhenRedelivered`, `refundEventsEachCreateOneNotification`, `otherUsersCannotSeeOrTouchMyNotifications` | Đạt |
| AC-15 | `unreadCountFollowsMarkReadAndReadAll`, `disablingAnOptionalTypeStopsThatTypeOnly`, `mandatoryTypesCannotBeDisabled`; `notifications.mock.spec.ts` (chuông, popover, đánh dấu đã đọc) | Đạt trên mock |

## Chưa đạt hoặc chưa chạy

| Mục | Lý do | Việc tiếp |
|---|---|---|
| AC-1, AC-4 (Stripe thật) | Thiếu Stripe test key, `stripe listen` | Pending "Cần người dùng" 4, 5 |
| AC-7 (camera thật) | Cần điện thoại và HTTPS | Pending "Chưa kiểm chứng" |
| AC-10, AC-11, AC-12 | Chưa có k3d; thiếu Telegram bot; object storage chưa chọn | Pending 1, 2 |
| AC-13 (Jaeger, Sentry) | Thiếu cụm, Sentry DSN; chưa chứng minh Gateway tự gắn `traceparent` khi proxy | Pending 3 |

## Review cuối sprint (hạng mục 13)

Review nội tuyến, không dùng subagent độc lập. Đọc `CheckInServiceImpl` và `PaymentRefundServiceImpl`.

**Kết luận: APPROVED cho phần code chạy được; CHANGES REQUESTED cho phần hạ tầng cho tới khi chạy được trên cụm** (AC-10 đến AC-13 chưa có bằng chứng).

Đã kiểm tốt:
- Check-in: quyền kiểm trước trạng thái vé (organizer khác không dò được); `markUsed` nguyên tử nên quét đồng thời chỉ một nơi vào; vé và sự kiện phải khớp.
- Hoàn tiền: bản ghi `refunds` commit trước khi gọi Stripe, thử lại dùng đúng bản ghi và key `refund-{id}`; số tiền do Payment tự tính, không tin event; lỗi mạng ném lại để thử lại, lỗi Stripe từ chối thì `REFUND_FAILED`.

Ghi chú (không chặn):
1. Check-in chưa chặn theo thời gian sự kiện (đã có ở pending 11). Vé hợp lệ quét sớm hàng ngày vẫn vào.
2. `onRefundSucceeded` hoàn tất lần hoàn `PENDING` mới nhất theo payment, không khớp theo `stripeRefundId`. Hiện an toàn vì mỗi payment chỉ có tối đa một lần `PENDING` tại một thời điểm; nếu sau này có hoàn một phần thì phải khớp theo `stripeRefundId` như `onRefundFailed`.
3. Số tiền lệch giữa event và Payment chỉ cảnh báo log; nên thêm metric nếu muốn alert.
4. Mọi tên metric, chart version và image trong hạng mục 10, 11 chưa đối chiếu với thực tế (pending 10).
