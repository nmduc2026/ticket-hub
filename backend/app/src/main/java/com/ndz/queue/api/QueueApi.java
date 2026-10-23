package com.ndz.queue.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Interface công khai của module Queue cho module khác gọi đồng bộ (ADR 0018). Booking gọi Queue chứ Queue không nghe
 * event của Booking: Modulith không cho hai module phụ thuộc lẫn nhau.
 */
public interface QueueApi {

	/** Khách có lượt vào còn hạn để chọn ghế không (BR-04; bước 0 của giữ ghế, overview.md §10). */
	boolean hasAdmission(UUID eventId, UUID userId);

	/** Dùng lượt vào: xoá key lượt vào, một lượt chỉ tạo được một booking (BR-04). */
	void consumeAdmission(UUID eventId, UUID userId);

	/**
	 * Booking vừa giữ ghế và đang chờ thanh toán: tính vào số người đang ở trong tới {@code countedUntil}, hạn dài nhất
	 * có thể của booking (hạn giữ ghế + ân hạn thanh toán, BR-03) để đếm dư chứ không đếm thiếu (overview.md §7.1).
	 */
	void bookingPending(UUID eventId, UUID bookingId, Instant countedUntil);

	/** Booking đã xác nhận, hết hạn hoặc bị huỷ: không còn chiếm chỗ trong phòng chờ. */
	void bookingSettled(UUID eventId, UUID bookingId);

}
