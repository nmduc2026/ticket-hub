# Review cuối Sprint 3

Người review: chính agent làm sprint (self-review theo phần B của `code-review`), 21/10/2026. Review độc lập bằng subagent cho phần thanh toán **chưa chạy**; nên chạy trước khi merge (tiền, oversell là vùng rủi ro cao).

## Phạm vi đã đọc

`StripeWebhookServiceImpl`, `BookingConfirmationServiceImpl` (xác nhận, đến muộn), toàn bộ luồng FE checkout, Helm, ArgoCD, CI. Bằng chứng chạy: `docs/test/sprint-3-verify.md`.

## Kết luận: APPROVED CÓ ĐIỀU KIỆN

Backend đạt: 266 + 9 test xanh, bất biến oversell có lớp chặn ở DB (`theDatabaseRefusesTwoLiveTicketsForOneSeat`), webhook và xác nhận đều idempotent. Điều kiện trước khi coi Sprint 3 là xong: chạy các mục "Chưa chạy" của báo cáo verify (Stripe thật, E2E, k3d).

## Phát hiện

| # | Mức | Vị trí | Vấn đề | Đề xuất |
|---|---|---|---|---|
| 1 | Trung bình | `BookingConfirmationServiceImpl.onPaymentSucceeded`, nhánh `default` | Booking đang `REFUND_REQUESTED` / `REFUNDED` / `REFUND_FAILED` mà nhận `PaymentSucceeded` chỉ log lỗi: tiền đã thu, không vé, không hoàn. Hiện chưa có đường tạo các trạng thái này (hoàn vé là Sprint 4) và chỉ số "đã thu mà chưa có vé hay hoàn" sẽ bắt được | Sprint 4: xử lý rõ khi làm hoàn vé; alert đã có chỉ số |
| 2 | Trung bình | FE `CheckoutPage` | Mở lại trang thanh toán sau khi Stripe đã nhận tiền nhưng chưa `CONFIRMED` hiện lại form (PI đã `succeeded`) | Thêm trạng thái payment vào API, hoặc FE nhớ trạng thái "đã trả" |
| 3 | Trung bình | FE | Đơn hết hạn rồi mới nhận tiền: FE chỉ chờ, không nói được "không giữ lại được ghế, sẽ hoàn tiền" | Cần tín hiệu từ BE (Sprint 4) |
| 4 | Thấp | CI `update-image-tags` | Push thẳng vào `main`; bị từ chối nếu bật bắt buộc PR | Cho phép bot hoặc đổi sang PR tự động |
| 5 | Thấp | Helm `core` | Postgres, Redis một replica, không backup; dữ liệu mất khi xoá PVC | Thay bằng CNPG ở Sprint 4 |
| 6 | Thấp | `app` Helm values | `KEYCLOAK_ISSUER_URI` công khai: Gateway trong cụm cần phân giải `auth.tickethub.localhost` | CoreDNS rewrite khi dựng cụm |

Không có phát hiện Critical hoặc High trong phạm vi đã đọc.

## Việc nên làm tiếp

1. Chạy review độc lập phần thanh toán (`PaymentServiceImpl`, `PaymentSettlementServiceImpl`, `PaymentRefundServiceImpl`, `PaymentReconciliationServiceImpl`).
2. Điền Stripe key, chạy `purchase.spec.ts`, đối chiếu prototype trên trình duyệt thật.
3. Dựng k3d theo `docs/ops/k8s-staging.md` và `docs/ops/gitops.md`.
