package com.ndz.booking.service;

/** Chỉ số đơn cần người xử lý và xuất ra metric (overview.md §16.3). */
public interface BookingInvariantService {

	/**
	 * Đếm đơn đang {@code REFUND_FAILED}, cập nhật gauge {@code tickethub.invariant.refund_failed_bookings} và trả số
	 * đơn. Lớn hơn 0 thì ghi log ERROR (cần Admin hoàn thủ công).
	 */
	int checkRefundFailedBookings();

}
