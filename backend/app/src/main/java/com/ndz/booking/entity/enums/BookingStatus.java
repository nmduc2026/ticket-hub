package com.ndz.booking.entity.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;

/**
 * Máy trạng thái booking (overview.md §7.7). Chỉ các chuyển trong bảng là hợp lệ; chuyển khác bị từ chối. Mỗi chuyển
 * trong DB còn được bảo vệ bằng điều kiện {@code WHERE status = <trạng thái cũ>} để hai tiến trình đồng thời không
 * cùng chuyển một booking.
 */
public enum BookingStatus {

	PENDING_PAYMENT, CONFIRMED, EXPIRED, CANCELLED, REFUND_REQUESTED, REFUNDED, REFUND_FAILED;

	private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = Map.of(
			PENDING_PAYMENT, EnumSet.of(CONFIRMED, EXPIRED, CANCELLED),
			// Thanh toán thành công đến muộn mà vẫn giữ lại được đúng các ghế đó (§7.3)
			EXPIRED, EnumSet.of(CONFIRMED),
			CANCELLED, EnumSet.of(CONFIRMED),
			CONFIRMED, EnumSet.of(REFUND_REQUESTED),
			REFUND_REQUESTED, EnumSet.of(REFUNDED, REFUND_FAILED),
			REFUND_FAILED, EnumSet.of(REFUND_REQUESTED),
			REFUNDED, EnumSet.noneOf(BookingStatus.class));

	public boolean canTransitionTo(BookingStatus next) {
		return ALLOWED.get(this).contains(next);
	}

	public void requireTransitionTo(BookingStatus next) {
		if (!canTransitionTo(next)) {
			throw new BusinessException(ErrorCode.INVALID_BOOKING_TRANSITION,
					"Không thể chuyển đơn từ %s sang %s".formatted(this, next));
		}
	}

}
