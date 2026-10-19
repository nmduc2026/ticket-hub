# 0022. Stripe charge model

**Trạng thái:** Accepted. Một số giả định cần kiểm ở spike Stripe Connect (Sprint 5), xem bên dưới.

## Bối cảnh
Marketplace: khách trả tiền, organizer nhận phần của mình, nền tảng giữ phí (BR-06). Kiểu charge của Stripe Connect quyết định ai chịu phí xử lý của Stripe, khi hoàn tiền có kéo lại tiền đã chuyển cho organizer không, và ai mất phí nền tảng khi hoàn:
- BR-07 (khách hoàn): khách nhận lại số đã trả trừ phí nền tảng, nền tảng giữ phí.
- BR-08 (sự kiện huỷ): khách nhận 100%, nền tảng trả lại cả phí.

## Các phương án
- **Direct charge:** tiền vào thẳng tài khoản organizer, nền tảng chỉ thu phí; nền tảng khó kiểm soát hoàn tiền.
- **Destination charge:** tiền vào tài khoản platform, Stripe tự chuyển phần của organizer (tổng trừ `application_fee`).
- **Separate charges & transfers:** platform tự chuyển tiền cho organizer; linh hoạt nhất nhưng phải tự đối soát.

## Quyết định
**Destination charge**, organizer dùng Connect Express.
- Hoàn tiền kéo lại phần đã chuyển (transfer reversal). BR-07: không hoàn phí nền tảng. BR-08: `refund_application_fee = true`.
- Organizer đã rút tiền mà số dư không đủ để kéo lại: Stripe trừ âm vào số dư connected account; Admin theo dõi qua audit log.
- **Giai đoạn đầu thu trên tài khoản platform**, chưa có Connect, để luồng mua vé chạy sớm (Sprint 3) mà không chờ spike. Từ đầu vẫn lưu `application_fee_minor`, và phần hoàn tiền chừa chỗ cho transfer reversal.
- Tài khoản platform đăng ký ở quốc gia Stripe hỗ trợ (vd. Singapore), chỉ test mode, vì Stripe chưa hỗ trợ merchant Việt Nam ở live mode.

**Giả định cần kiểm ở spike:**
- Thẻ thường không dừng ở `processing`.
- Không huỷ được PaymentIntent đã `succeeded`.
- Event của connected account đi qua endpoint Connect với signing secret riêng.
- Test mode có rate limit, không dùng để load test.
- Platform Singapore thu VND, connected account Express trong test mode: **chưa kiểm chứng**.

Spike cho kết quả khác thì cập nhật ADR này (hoặc viết ADR mới thay thế).

## Hệ quả
- (+) Dòng tiền và số tiền hoàn tính được rõ ràng theo BR-07 / BR-08.
- (+) Nền tảng kiểm soát hoàn tiền.
- (−) Phí xử lý của Stripe tính trên tài khoản platform.
- (−) Chuyển từ platform-only sang Connect là một bước thay đổi ở Sprint 6.

Liên quan: BR-05, BR-06, BR-07, BR-08; `overview.md` §3 (ghi chú tiền tệ), §7.6, §7.8, §11.2, ADR 0019, 0023.
