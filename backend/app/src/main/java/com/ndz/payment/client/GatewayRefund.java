package com.ndz.payment.client;

/** Kết quả tạo hoàn tiền ở Stripe; {@code status} giữ nguyên chuỗi của Stripe ({@code succeeded}, {@code pending}...). */
public record GatewayRefund(String id, String status) {

	public boolean isSucceeded() {
		return "succeeded".equals(status);
	}

	/** Stripe từ chối hoặc huỷ ngay khi tạo; các trạng thái còn lại ({@code pending}...) chờ webhook. */
	public boolean isFailed() {
		return "failed".equals(status) || "canceled".equals(status);
	}

}
