package com.ndz.booking.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.ndz.booking.dto.response.BookingViews.AdminBookingSummary;
import com.ndz.booking.dto.response.BookingViews.BookingSummary;
import com.ndz.booking.dto.response.BookingViews.BookingView;
import com.ndz.booking.dto.response.BookingViews.ConfirmationData;
import com.ndz.booking.dto.response.BookingViews.HoldExpiringBooking;
import com.ndz.booking.dto.response.BookingViews.HeldSeat;
import com.ndz.booking.dto.response.BookingViews.LockedBooking;
import com.ndz.booking.dto.response.BookingViews.LockedSeat;
import com.ndz.booking.dto.response.BookingViews.PaymentInfo;
import com.ndz.booking.dto.response.BookingViews.RefundContext;
import com.ndz.booking.entity.enums.BookingStatus;

/** Phần của {@link BookingRepository} cần khoá dòng, {@code RETURNING} hoặc ghép nhiều bảng thành view. */
public interface BookingRepositoryCustom {

	/** Khoá các ghế (theo thứ tự seat_id) và trả về trạng thái hiện tại; ghế không có trong tồn kho không được trả về. */
	List<LockedSeat> lockSeats(UUID eventId, List<UUID> seatIds);

	/** Khoá dòng booking để chuyển trạng thái; người khác chờ tới khi transaction này kết thúc. */
	Optional<LockedBooking> lockBooking(UUID bookingId);

	/** Booking của chính người dùng (chống IDOR); người khác hoặc không tồn tại đều cho kết quả rỗng. */
	Optional<BookingView> findOwned(UUID bookingId, UUID userId);

	/** Đơn của chính người dùng, mới tạo trước, theo trang (chống IDOR: lọc theo {@code user_id}). */
	List<BookingSummary> findByUser(UUID userId, int limit, int offset, Duration refundCutoff);

	/** Đơn theo trạng thái cho Admin, đơn hoàn thất bại lâu nhất lên trước (cần xử lý trước). */
	List<AdminBookingSummary> findByStatus(BookingStatus status, int limit, int offset);

	RefundContext refundContext(UUID bookingId);

	/** Số tiền, tiền tệ và mức phí nền tảng đã chốt ở bản sao sự kiện (BR-06) của booking. */
	PaymentInfo paymentInfo(UUID bookingId);

	/** Tên sự kiện, giờ diễn, địa điểm, ghế và email liên hệ của booking, đọc từ bản sao sự kiện của Booking. */
	ConfirmationData confirmationData(UUID bookingId);

	/** Nhả các ghế đang giữ của booking về {@code AVAILABLE}; trả về id các ghế đã nhả. */
	@Transactional
	List<UUID> releaseSeats(UUID bookingId);

	/**
	 * Đánh dấu đã báo "sắp hết giờ giữ ghế" cho tối đa {@code limit} booking {@code PENDING_PAYMENT} chưa bắt đầu
	 * thanh toán, chưa báo, còn hạn nhưng hết hạn trước {@code threshold}; trả về các booking vừa đánh dấu.
	 */
	@Transactional
	List<HoldExpiringBooking> markHoldExpiringNotified(Instant now, Instant threshold, int limit);

	/** Nhả các ghế đã bán của booking đã hoàn về {@code AVAILABLE}; trả về id các ghế đã nhả. */
	@Transactional
	List<UUID> releaseSoldSeats(UUID bookingId);

	/** Ghi một vé đã thu hồi; gửi lại cùng vé không đếm thêm. */
	@Transactional
	void recordRevokedTicket(UUID bookingId, UUID ticketId, Instant now);

	int revokedTicketCount(UUID bookingId);

	/** Mọi ghế đang {@code HELD} của booking còn chờ thanh toán, để dựng lại key Redis. */
	List<HeldSeat> findHeldSeats();

	/** Id các ghế của một booking. */
	List<UUID> seatIdsOf(UUID bookingId);

	/** Thêm các ghế của đơn mới tạo (cùng transaction với việc tạo đơn). */
	@Transactional
	void insertItems(UUID bookingId, UUID eventId, List<LockedSeat> seats);

}
