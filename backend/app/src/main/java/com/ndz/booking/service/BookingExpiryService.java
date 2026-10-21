package com.ndz.booking.service;

/** Hết hạn giữ ghế (BR-03, overview.md §7.3). */
public interface BookingExpiryService {

	/**
	 * Chuyển booking quá hạn giữ ghế (kể cả ân hạn khi đã bắt đầu thanh toán) sang EXPIRED và nhả ghế, theo lô.
	 *
	 * @return số booking đã hết hạn
	 */
	int expireDueBookings();

	/**
	 * Phát {@code BookingHoldExpiring} cho booking chưa bắt đầu thanh toán còn ≤ 2 phút giữ ghế, mỗi booking một lần.
	 *
	 * @return số booking đã báo
	 */
	int notifyExpiringHolds();

}
