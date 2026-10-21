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
 * @param expiryBatchSize số booking quá hạn xử lý mỗi lượt của job
 */
@ConfigurationProperties("tickethub.booking")
public record BookingProperties(@DefaultValue("10m") Duration holdDuration,
		@DefaultValue("6") int maxSeatsPerBooking, @DefaultValue("5m") Duration paymentGrace,
		@DefaultValue("100") int expiryBatchSize) {
}
