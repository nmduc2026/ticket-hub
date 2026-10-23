package com.ndz.payment.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình thanh toán, mặc định theo overview.md §7.3.
 *
 * @param reconcileAfter payment chưa kết thúc quá lâu này thì đối soát hỏi Stripe (15 phút)
 * @param unsettledAlertAfter payment đã thu tiền mà booking chưa {@code CONFIRMED} / chưa được hoàn quá lâu này thì tính
 *        vào chỉ số cảnh báo (5 phút)
 * @param reconcileBatchSize số payment đối soát mỗi lượt của job
 * @param refundQueueRate số lệnh hoàn gửi Stripe tối đa mỗi giây khi huỷ sự kiện (BR-08: 10, dưới bulkhead 20)
 * @param refundQueueBatchSize số dòng hàng đợi nhận mỗi lượt của job
 * @param refundQueueLease thời gian giữ một dòng đã nhận; pod chết thì dòng quay lại sau khoảng này
 * @param refundQueueMaxAttempts số lần thử tối đa khi Stripe lỗi tạm thời, quá thì đánh dấu lỗi để Admin xử lý
 */
@ConfigurationProperties("tickethub.payment")
public record PaymentProperties(@DefaultValue("15m") Duration reconcileAfter,
		@DefaultValue("5m") Duration unsettledAlertAfter, @DefaultValue("100") int reconcileBatchSize,
		@DefaultValue("10") int refundQueueRate, @DefaultValue("50") int refundQueueBatchSize,
		@DefaultValue("2m") Duration refundQueueLease, @DefaultValue("8") int refundQueueMaxAttempts) {
}
