package com.ndz.booking.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Interface công khai của module Booking cho module khác gọi đồng bộ khi người dùng đang chờ kết quả (ADR 0018). Khi
 * tách service, thay cài đặt bằng lời gọi API nội bộ; code gọi giữ nguyên.
 */
public interface BookingApi {

	/**
	 * Bắt đầu thanh toán một booking (overview.md §7.2): kiểm chủ sở hữu, trạng thái chờ thanh toán và hạn giữ ghế còn
	 * tối thiểu 90 giây (BR-03), rồi ghi {@code payment_started_at} để booking được ân hạn thêm 5 phút. Gọi lại cho
	 * booking đã bắt đầu thanh toán thì không đổi mốc và không kiểm lại 90 giây.
	 *
	 * @throws com.ndz.shared.exception.BusinessException {@code BOOKING_NOT_FOUND} (không có hoặc không phải của người
	 *         gọi), {@code BOOKING_NOT_PAYABLE} (không còn chờ thanh toán) hoặc {@code HOLD_EXPIRING}
	 */
	PaymentStart startPayment(UUID userId, UUID bookingId);

	/**
	 * Trong các booking đã cho, những booking chưa {@code CONFIRMED} (kể cả booking không tồn tại). Payment dùng để đếm
	 * "đã thu tiền mà booking chưa xác nhận" cho cảnh báo (overview.md §16.3), không đọc bảng của Booking.
	 */
	Set<UUID> notConfirmed(Collection<UUID> bookingIds);

	/** Đơn của một người dùng, mới tạo trước, tối đa {@code limit} dòng (Admin xem chi tiết người dùng). */
	List<UserBooking> bookingsOf(UUID userId, int limit);

	/** Doanh thu gộp, phí và số vé của mọi organizer trong khoảng, cho Admin dashboard (§11.2). */
	AdminTotals adminTotals(Instant from, Instant to);

	/** Số đơn đang cần đối soát (cờ {@code needs_reconcile}, Payment đặt khi phát hiện lệch). */
	long countNeedsReconcile();

	/** Số đơn ở trạng thái {@code REFUND_FAILED} (cần Admin hoàn thủ công). */
	long countRefundFailed();

	record AdminTotals(long grossMinor, long feeMinor, int ticketsSold) {
	}

	/** Thông tin cần để gửi lại email vé (Ticket module, #27 "Gửi lại email vé hàng loạt"); rỗng nếu không có booking. */
	Optional<BookingContact> contactOf(UUID bookingId);

	/** {@code status}: tên trạng thái booking, vd. {@code CONFIRMED}. */
	record BookingContact(UUID organizerId, String status, String contactEmail, String eventTitle, Instant startsAt,
			String venueName) {
	}

	/**
	 * Một đơn trong danh sách của người dùng; {@code refundStatus} là {@code REQUESTED}, {@code REFUNDED},
	 * {@code FAILED} khi đơn đã qua bước yêu cầu hoàn, ngược lại null.
	 */
	record UserBooking(UUID bookingId, String eventTitle, Instant startsAt, String status, long totalMinor,
			String currency, Instant createdAt, String refundStatus, Long refundAmountMinor, int ticketCount) {
	}

	/**
	 * Số tiền cần thu, mức phí nền tảng đã chốt khi publish (BR-06) và organizer nhận tiền (destination charge, §7.8).
	 * Tiền là số nguyên đơn vị nhỏ nhất (BR-10).
	 */
	record PaymentStart(UUID bookingId, long amountMinor, String currency, int platformFeeBps, UUID organizerId) {
	}

}
