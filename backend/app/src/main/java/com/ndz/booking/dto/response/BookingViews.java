package com.ndz.booking.dto.response;

import com.ndz.booking.entity.enums.BookingStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Giá trị của đơn đặt vé dùng giữa các lớp của module booking. */
public final class BookingViews {

	private BookingViews() {
	}

	/** Thông tin sự kiện Booking cần để kiểm quy tắc đặt vé (từ {@code event_snapshots}). */
	public record EventRules(UUID eventId, String status, int maxTicketsPerUser, Instant saleStartsAt) {
	}

	/** Ghế đang giữ cùng hạn của booking giữ nó, để dựng lại key Redis khi Redis phục hồi (overview.md §10). */
	public record HeldSeat(UUID eventId, UUID seatId, UUID bookingId, Instant expiresAt, Instant paymentStartedAt) {
	}

	/** Một ghế đã khoá trong tồn kho. */
	public record LockedSeat(UUID seatId, UUID tierId, String tierName, String sectionCode, String rowLabel,
			int seatNumber, long priceMinor, String currency, String status) {
	}

	/** Dòng booking đang bị khoá để chuyển trạng thái. */
	public record LockedBooking(UUID id, UUID userId, UUID eventId, BookingStatus status, Instant expiresAt,
			Instant paymentStartedAt) {
	}

	public record BookingItemView(UUID seatId, String sectionCode, String rowLabel, int seatNumber, UUID tierId,
			String tierName, long priceMinor) {
	}

	public record BookingView(UUID id, UUID eventId, String status, long totalMinor, String currency,
			Instant expiresAt, String latePayment, List<BookingItemView> items) {
	}

	/** Booking bị ảnh hưởng khi sự kiện tạm dừng bán (BR-13): đủ dữ liệu để phát {@code BookingSalePaused}. */
	public record AffectedBooking(UUID bookingId, UUID userId, String contactEmail, String status) {
	}

	/** Booking sắp hết giờ giữ ghế, đủ dữ liệu để phát {@code BookingHoldExpiring}. */
	public record HoldExpiringBooking(UUID bookingId, UUID userId, UUID eventId, String eventTitle, String contactEmail,
			Instant expiresAt) {
	}

	/** Dữ liệu Booking đóng gói vào {@code BookingConfirmed} cho Ticket và Notification (không cần bản sao riêng). */
	public record ConfirmationData(UUID bookingId, UUID userId, UUID eventId, String eventTitle, Instant startsAt,
			String venueName, String contactEmail, long totalMinor, String currency, UUID organizerId,
			List<BookingItemView> items) {
	}

	/** Số tiền và mức phí của booking để Payment tạo thanh toán. */
	public record PaymentInfo(long totalMinor, String currency, int platformFeeBps) {
	}

	/**
	 * Một dòng trong "Đơn của tôi": đơn kèm tên sự kiện và giờ diễn từ bản sao sự kiện. {@code refundStatus} là
	 * {@code REQUESTED}, {@code REFUNDED}, {@code FAILED} khi đơn đã qua bước yêu cầu hoàn, ngược lại null.
	 */
	public record BookingSummary(UUID bookingId, String eventTitle, Instant startsAt, String status, long totalMinor,
			String currency, Instant createdAt, String refundStatus, Long refundAmountMinor, int ticketCount,
			long platformFeeMinor, Long refundableAmountMinor, Instant refundDeadline) {
	}

	/** Một dòng trong danh sách đơn của Admin; {@code failedAt} chỉ có với đơn từng hoàn thất bại. */
	public record AdminBookingSummary(UUID bookingId, String userEmail, String eventTitle, long totalMinor,
			String currency, Instant failedAt) {
	}

	/** Lý do và số tiền hoàn đã chốt lúc yêu cầu hoàn, để Admin gửi lại lần hoàn mới đúng như cũ. */
	public record RefundContext(String reason, long amountMinor) {
	}

	/** Kết quả yêu cầu hoàn vé: số tiền khách sẽ nhận lại (BR-07). */
	public record RefundResult(UUID bookingId, BookingStatus status, long refundAmountMinor) {
	}

}
