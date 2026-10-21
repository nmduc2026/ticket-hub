package com.ndz.booking.service;

import java.util.List;
import java.util.UUID;

import com.ndz.booking.dto.response.BookingViews.AdminBookingSummary;
import com.ndz.booking.dto.response.BookingViews.RefundResult;
import com.ndz.booking.entity.enums.BookingStatus;

/** Việc của Admin trên đơn đặt vé: tìm đơn theo trạng thái và hoàn thủ công đơn hoàn thất bại (overview.md §7.4). */
public interface BookingAdminService {

	/** Đơn của mọi người dùng theo trạng thái, theo trang. */
	List<AdminBookingSummary> listByStatus(BookingStatus status, int page, int size);

	/**
	 * Hoàn thủ công đơn {@code REFUND_FAILED}: về {@code REFUND_REQUESTED} và phát lại {@code RefundRequested} với lý do
	 * và số tiền đã chốt, để Payment tạo lần hoàn mới (key mới). Vé vẫn {@code REVOKED}.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code BOOKING_NOT_FOUND} hoặc {@code BOOKING_NOT_REFUNDABLE}
	 */
	RefundResult retryRefund(UUID adminId, UUID bookingId);

}
