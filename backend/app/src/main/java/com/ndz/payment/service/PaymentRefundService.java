package com.ndz.payment.service;

import java.util.UUID;

import com.ndz.booking.event.RefundRequested;

/** Hoàn tiền (overview.md §7.3, §7.4, BR-07, BR-10, ADR 0026). */
public interface PaymentRefundService {

	/**
	 * Thanh toán đến muộn mà booking không giữ lại được ghế: hoàn 100% cho payment. Mỗi lần thử là một bản ghi
	 * {@code refunds} có id sinh trước khi gọi Stripe (idempotency key {@code refund-{refundId}}); nhận lại event thì
	 * dùng lại bản ghi cũ nên không hoàn hai lần. Idempotent theo {@code messageId}. Người gọi (listener) mở transaction.
	 */
	void refundFully(UUID messageId, UUID paymentId, String reason);

	/**
	 * Hoàn theo {@code RefundRequested} của booking. Số tiền do Payment tính từ payment đã thu: lý do khách yêu cầu hoàn
	 * thì trừ {@code application_fee_minor}, các lý do còn lại hoàn 100% (BR-07, BR-08). Mỗi event là một lần thử mới
	 * (key {@code refund-{refundId}} mới) trừ khi lần trước còn chờ Stripe. Kết quả đi ra bằng {@code RefundCompleted}
	 * hoặc {@code RefundFailed}. Idempotent theo {@code messageId}. Người gọi (listener) mở transaction.
	 */
	void refundForBooking(RefundRequested event);

	/**
	 * Webhook {@code charge.refunded}: lần hoàn đang chờ của payment đã xong → {@code RefundCompleted}. Không có lần nào
	 * đang chờ (đã xong hoặc đã thất bại) thì bỏ qua và ghi log.
	 */
	void onRefundSucceeded(UUID paymentId);

	/** Xếp yêu cầu hoàn vì huỷ sự kiện vào hàng đợi (mỗi booking một dòng; gửi lại event không xếp thêm). */
	void enqueueForCancelledEvent(RefundRequested event);

	/**
	 * Một lượt của job: nhận các dòng tới hạn, gọi Stripe theo giới hạn tốc độ; lỗi tạm thời thử lại có backoff.
	 *
	 * @return số dòng đã xử lý xong trong lượt
	 */
	int processRefundQueue();

	/** Webhook {@code refund.failed}: lần hoàn đang chờ thất bại → {@code RefundFailed}. Lần đã chốt thì bỏ qua. */
	void onRefundFailed(UUID paymentId, String stripeRefundId, String failureCode);

}
