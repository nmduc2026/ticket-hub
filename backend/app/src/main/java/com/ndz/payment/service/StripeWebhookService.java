package com.ndz.payment.service;

/** Xử lý webhook của Stripe (overview.md §7.3, §7.7, §13.4). */
public interface StripeWebhookService {

	/**
	 * Xác thực chữ ký trên raw body, khử trùng theo {@code stripe_event_id}, rồi áp dụng sự kiện vào máy trạng thái của
	 * payment và phát {@code PaymentSucceeded} / {@code PaymentFailed}. Event sai thứ tự hoặc không thuộc payment nào
	 * thì bị bỏ qua và ghi log, vẫn trả thành công để Stripe không gửi lại vô ích.
	 *
	 * @param payload nguyên văn body Stripe đã ký
	 * @param signatureHeader giá trị header {@code Stripe-Signature}
	 * @throws com.ndz.shared.exception.BusinessException {@code WEBHOOK_SIGNATURE_INVALID} (chữ ký sai hoặc lệch quá
	 *         5 phút), không đổi trạng thái gì
	 */
	void handle(String payload, String signatureHeader);

	/**
	 * Lấy lại event từ Stripe theo id và áp dụng như một webhook mới tới (Admin màn #36, không cần chữ ký vì gọi trực
	 * tiếp Stripe API bằng secret key). Idempotent theo {@code stripe_event_id}: event đã xử lý thì chỉ bỏ qua, không
	 * đổi trạng thái payment.
	 *
	 * @return true nếu vừa xử lý (lần đầu), false nếu đã xử lý từ trước
	 */
	boolean reprocess(String stripeEventId);

}
