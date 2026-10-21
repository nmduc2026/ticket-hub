# Báo cáo verify Sprint 3

Nhánh `feature/sprint-3`. Chạy ngày 21/10/2026 trên máy dev (Windows 11), Docker bật. Plan: `docs/plans/2026-10-19-sprint-3-plan.md`, hạng mục 15. Việc cần người dùng hoặc cụm Kubernetes nằm ở `docs/plans/2026-10-19-sprint-3-pending.md`.

## Lệnh đã chạy

| Lệnh | Kết quả |
|---|---|
| `cd backend && ./mvnw verify -Dspotless.check.skip=true` (Testcontainers) | BUILD SUCCESS: `app` 266 test, `gateway` 9 test, 0 lỗi, 0 bỏ qua; `git status` sạch sau khi chạy (`openapi.json` không lệch) |
| `cd backend && ./mvnw spotless:check` | Không báo vi phạm |
| `cd frontend && npm run lint` / `npm run typecheck` | Không cảnh báo, không lỗi |
| `cd frontend && npx vitest run` | 19 file, 100 test xanh (lần đầu 2 test cũ của `OrganizerEventsPage` quá 5 giây khi chạy cùng `mvnw verify`; chạy lại một mình xanh) |
| `cd frontend && npx vite build` | Thành công |
| `cd frontend && npx playwright test mock` (Vite + route mock, 1280px và 390px) | 13 xanh, 1 bỏ qua có chủ đích (như Sprint 2) |
| `helm lint` + `helm template` cho `tickethub-lib`-based `app`, `gateway`, `frontend`, `core` | Sạch, 0 lỗi |
| PyYAML đọc mọi file `infra/argocd`, `infra/k8s-platform`, `infra/k3d`, `ci.yml` | Hợp lệ cú pháp |
| gitleaks (pre-commit) mỗi commit | Không có secret |

## Truy vết AC

| AC | Bằng chứng | Kết quả |
|---|---|---|
| AC-1 (BR-03) | `PaymentApiTests`: `lessThanNinetySecondsLeftIsRejected`, `exactlyNinetySecondsLeftIsAccepted`, `startsPaymentAndRecordsTheStartAndTheFee`, `aBookingThatStartedPaymentIsNotExpiredDuringTheGracePeriod`, `startingAgainAfterTheGracePeriodIsRejected`; FE `checkoutPayment.test.tsx` (còn < 90 giây khoá, `HOLD_EXPIRING`) | Đạt |
| AC-2 | BE: `PaymentFlowTests.paidBookingIsConfirmedAndItsSeatsAreSold`, `TicketApiTests.everySeatGetsOneValidTicketAndTheBookingGetsOneTicketIssued`, `TicketEmailTests.ticketIssuedSendsTheTicketEmail`; FE: test trả tiền xong chờ `CONFIRMED` rồi sang trang thành công. Luồng thẻ 4242 thật + email trong Mailpit 2 phút: `e2e/purchase.spec.ts` | BE, FE đạt trên mock; **chưa chạy với Stripe thật** |
| AC-3 | `PaymentApiTests.sameKeyReturnsTheSameResponseAndCreatesTheIntentOnce`, `anotherKeyForTheSameBookingStillYieldsOnePayment`, `concurrentRequestsCreateExactlyOnePayment`; `StripeWebhookApiTests.failedWhileProcessingReturnsToRequiresPaymentAndAnotherCardCanSucceed`; FE test thẻ bị từ chối báo lỗi và mở lại form | Đạt (thẻ `4000…0002` thật chưa chạy) |
| AC-4 | `StripeWebhookApiTests`: sai chữ ký, chữ ký secret khác, thiếu header, lệch > 5 phút, `signatureCoversTheRawBodyByteForByte`, `sameEventTwiceIsProcessedOnce`, `webhookNeedsNoBearerToken`; `BffLoginFlowTests` (webhook qua Gateway không bị CSRF) | Đạt |
| AC-5 | `PaymentReconciliationTests.aLostSucceededWebhookIsRepairedByReconciliation`, `reconciliationIsIdempotent`, `succeededButUnconfirmedPaymentsAreCounted` | Đạt |
| AC-6 | `LatePaymentTests.expiredBookingWithFreeSeatsIsConfirmed`, `seatTakenByAnotherCustomerMeansAFullRefundAndTheBookingStaysExpired` | Đạt |
| AC-7 (BR-12) | `PaymentFlowTests.cancellingTheBookingCancelsThePaymentIntent`, `expiredBookingCancelsThePaymentIntent`, `cancelFailingBecauseTheIntentAlreadySucceededIsTreatedAsPaid` | Đạt |
| AC-8 (BR-10, BR-06) | `PlatformFeeTests` (làm tròn xuống, 333.333 × 500 → 16.666), `PaymentApiTests.feeIsRoundedDown` | Đạt |
| AC-9 | `TicketApiTests.qrTokenVerifiesWithThePublicKeyLookedUpByKid`, `iOnlySeeMyOwnTickets`; `MyBookingsApiTests.iOnlySeeMyOwnBookingsNewestFirst`; FE `myTickets.test.tsx` | Đạt |
| AC-10 | `e2e/purchase.spec.ts` + truy vấn ghế trùng. Phần BE: `TicketApiTests.theDatabaseRefusesTwoLiveTicketsForOneSeat` | **Chưa chạy** (cần Stripe test key, `stripe listen`) |
| AC-11 | `helm lint` / `template` sạch; Trivy và OWASP Dependency-Check đã thêm vào CI; ArgoCD, Envoy Gateway, NetworkPolicy chưa chạy trên cụm | **Chưa kiểm chứng** (chưa có k3d) |

## Chưa đạt hoặc chưa chạy

| Mục | Lý do | Việc tiếp |
|---|---|---|
| AC-2 (phần Stripe thật), AC-3 (thẻ `4000…0002` thật), AC-10 | Thiếu Stripe test key và `stripe listen` | Pending hạng mục 1, 14 |
| AC-11 | Chưa dựng k3d, ArgoCD | Pending hạng mục 12, 13 |
| Đối chiếu prototype #6–#9, #21 trên trình duyệt thật, 390px, bàn phím | Chưa chạy; chỉ có test component | Pending hạng mục 10 |
| Trivy, OWASP Dependency-Check, job `update-image-tags` | Chỉ chạy được trên GitHub Actions sau khi push | Quan sát CI khi mở PR |

## Rủi ro còn lại

- Webhook và đối soát đã có test với Stripe giả (WireMock); hành vi thật của Stripe (thứ tự sự kiện, 3DS) chưa quan sát.
- CSP nginx đã mở cho Stripe nhưng chưa thử với Payment Element thật.
