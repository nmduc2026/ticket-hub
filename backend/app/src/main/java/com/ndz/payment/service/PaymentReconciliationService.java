package com.ndz.payment.service;

/** Đối soát payment với Stripe khi webhook đến trễ hoặc mất (overview.md §7.3, ADR 0019). */
public interface PaymentReconciliationService {

	/**
	 * Quét mọi payment chưa kết thúc ({@code REQUIRES_PAYMENT}, {@code PROCESSING}) quá 15 phút, hỏi trạng thái trực
	 * tiếp từ Stripe và xử lý như webhook tương ứng. Mỗi payment một transaction riêng: một payment lỗi không chặn các
	 * payment khác. Cũng cập nhật chỉ số {@code tickethub.payment.succeeded_unsettled}. Chạy lại không gây sai.
	 *
	 * @return số payment đã đổi trạng thái
	 */
	int reconcile();

}
