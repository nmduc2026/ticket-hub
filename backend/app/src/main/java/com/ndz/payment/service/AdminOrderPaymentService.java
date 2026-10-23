package com.ndz.payment.service;

import java.util.UUID;

import com.ndz.payment.dto.response.AdminOrderPaymentResponses.OrderPayment;

/** Chi tiết thanh toán của đơn cho Admin (màn #29). */
public interface AdminOrderPaymentService {

	/** @throws com.ndz.shared.exception.BusinessException {@code BOOKING_NOT_FOUND} nếu đơn chưa có payment */
	OrderPayment detail(UUID bookingId);

}
