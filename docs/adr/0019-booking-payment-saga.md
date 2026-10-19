# 0019. Saga đặt vé – thanh toán

**Trạng thái:** Accepted

## Bối cảnh
Bất biến số 2: đã trừ tiền thì phải có vé hoặc được hoàn tự động. Các kịch bản làm vỡ bất biến:
- **Mất webhook `succeeded`.** Với thẻ, PaymentIntent thường đi thẳng từ `requires_payment_method` / `requires_action` sang `succeeded`, không dừng ở `processing`. Nếu đối soát chỉ quét `PROCESSING` thì bản ghi local kẹt `REQUIRES_PAYMENT` → booking hết hạn → huỷ PI thất bại (PI đã succeeded) → khách mất tiền, không có vé, không ai hoàn. Nguyên nhân hay gặp: Gateway chặn webhook vì CSRF, sai secret, deploy hỏng.
- **Phát vé vi phạm UNIQUE.** Coi là lỗi tạm thời thì retry rồi vào DLT; bất biến bị vi phạm cho tới khi có người xử lý tay.
- **Trạng thái payment `FAILED`.** Sau `payment_failed`, PI quay về `requires_payment_method` và dùng lại được; `booking_id UNIQUE` nên mỗi booking chỉ có 1 payment. `FAILED` là trạng thái cuối thì khách không thử thẻ khác được; không phải cuối thì thành chuyển lùi.

## Các phương án
- **Orchestrator riêng** điều phối từng bước: luồng tập trung một chỗ, nhưng thêm một thành phần phải vận hành, chưa cần ở quy mô này.
- **Choreography** (mỗi module phản ứng với event) + máy trạng thái tường minh + đối soát.

## Quyết định
**Choreography**, kèm:
- **Máy trạng thái tường minh** cho Booking, Payment, Sự kiện; chuyển trạng thái ngoài bảng bị từ chối và ghi log. Payment không có `FAILED`: "thanh toán thất bại" là event, không phải trạng thái.
- **Đối soát quét mọi payment chưa kết thúc** (`REQUIRES_PAYMENT`, `PROCESSING`) quá 15 phút, hỏi Stripe và xử lý như webhook tương ứng.
- Huỷ PI thất bại vì PI đã `succeeded` → xử lý như `PaymentSucceeded` (nhánh "thanh toán thành công nhưng booking đã hết hạn").
- Vi phạm UNIQUE khi phát vé là **lỗi nghiệp vụ**: phát `TicketIssuanceFailed` → hoàn 100% tự động + alert mức cao (lớp 2 chống oversell đã lọt). Lỗi hạ tầng thì retry có backoff → DLT.
- Alert bất biến: payment `SUCCEEDED` mà booking chưa `CONFIRMED` hay chưa được hoàn sau 5 phút.

## Hệ quả
- (+) Mỗi cách tiền có thể "kẹt" đều có đường tự sửa hoặc alert.
- (+) Máy trạng thái là căn cứ để bỏ qua webhook sai thứ tự.
- (−) Luồng rải ra nhiều module, khó theo dõi hơn orchestrator; bù lại bằng trace và log theo `bookingId`.
- (−) Mỗi nhánh bù trừ cần một test tích hợp riêng, gồm kịch bản mất webhook.

Liên quan: `overview.md` §7.2, §7.3, §7.7, §16.3, §20.1, ADR 0022, 0026.
