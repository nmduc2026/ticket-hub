package com.ndz.booking.entity.enums;

/**
 * Tiền của thanh toán đến muộn mà booking không giữ lại được ghế (overview.md §7.3): Payment hoàn tự động 100%, cột
 * này cho khách thấy tiền đang hoàn, đã hoàn hay hoàn lỗi (Admin xử lý tay).
 */
public enum LatePayment {

	REFUND_PENDING, REFUNDED, REFUND_FAILED

}
