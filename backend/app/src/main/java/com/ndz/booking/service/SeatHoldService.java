package com.ndz.booking.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lớp 1 giữ ghế bằng Redis (overview.md §10, BR-01 đến BR-03). Redis chỉ chặn sớm các request tranh cùng ghế; Postgres
 * vẫn là nơi quyết định nên mất key hay Redis lệch không bao giờ làm bán trùng. Redis không dùng được thì giữ ghế
 * dừng hẳn (fail-closed) thay vì lặng lẽ bỏ qua lớp này.
 */
public interface SeatHoldService {

	/**
	 * Giữ cả nhóm ghế cho booking: có một ghế đang bị giữ thì không ghế nào được giữ. Key sống tới hạn giữ ghế của
	 * Postgres cộng thêm một khoảng đệm. Lớp Redis tắt thì không làm gì.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code SEAT_UNAVAILABLE} khi có ghế đang bị giữ,
	 *         {@code SEAT_HOLD_UNAVAILABLE} (503, kèm {@code Retry-After}) khi Redis không dùng được
	 */
	void acquire(UUID eventId, List<UUID> seatIds, UUID bookingId, Instant expiresAt);

	/** Nhả ngay các key còn thuộc booking này (so giá trị). Lỗi Redis chỉ ghi log: key tự hết hạn theo TTL. */
	void release(UUID eventId, List<UUID> seatIds, UUID bookingId);

	/** Như {@link #release} nhưng chỉ chạy sau khi transaction hiện tại commit (nếu có). */
	void releaseAfterCommit(UUID eventId, List<UUID> seatIds, UUID bookingId);

	/** Gia hạn key tới {@code holdUntil} (cộng đệm) khi booking bắt đầu thanh toán và được ân hạn (BR-03). */
	void extend(UUID eventId, List<UUID> seatIds, UUID bookingId, Instant holdUntil);

	/** Lớp Redis đang bật. */
	boolean isEnabled();

	/** Bật / tắt lớp Redis khi chạy (tắt = chỉ còn Postgres, vẫn đúng bất biến, chỉ chậm hơn dưới tải). */
	void setEnabled(boolean enabled);

	/** Sẵn sàng nhận giữ ghế: lớp tắt, hoặc Redis sống và key đã được dựng lại. */
	boolean isAvailable();

	/**
	 * Kiểm tra Redis (job gọi định kỳ). Redis hỏng thì chuyển sang không sẵn sàng; Redis vừa phục hồi hoặc mất dữ liệu
	 * thì dựng lại key {@code seat:*} từ các dòng {@code HELD} của Postgres rồi mới mở lại giữ ghế.
	 */
	void probe();

}
