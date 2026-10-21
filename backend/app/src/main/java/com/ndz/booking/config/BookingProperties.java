package com.ndz.booking.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình đặt vé, mặc định theo BR.
 *
 * @param holdDuration thời gian giữ ghế kể từ khi tạo booking (BR-03: 10 phút)
 * @param maxSeatsPerBooking số ghế tối đa của một booking (BR-02: 6)
 * @param paymentGrace ân hạn thêm khi booking đã bắt đầu thanh toán trước hạn giữ ghế (BR-03: 5 phút)
 * @param paymentStartMinRemaining thời gian giữ ghế tối thiểu còn lại để được bắt đầu thanh toán (BR-03: 90 giây)
 * @param expiryBatchSize số booking quá hạn xử lý mỗi lượt của job
 * @param refundCutoff khách chỉ được yêu cầu hoàn khi còn ít nhất khoảng này trước giờ diễn (BR-07: 48 giờ)
 * @param holdExpiringNotice báo "sắp hết giờ giữ ghế" khi booking chưa bắt đầu thanh toán còn ít hơn khoảng này
 *        (overview.md §6.5: 2 phút)
 */
@ConfigurationProperties("tickethub.booking")
public record BookingProperties(@DefaultValue("10m") Duration holdDuration,
		@DefaultValue("6") int maxSeatsPerBooking, @DefaultValue("5m") Duration paymentGrace,
		@DefaultValue("90s") Duration paymentStartMinRemaining, @DefaultValue("100") int expiryBatchSize,
		@DefaultValue("48h") Duration refundCutoff, @DefaultValue("2m") Duration holdExpiringNotice) {
}
