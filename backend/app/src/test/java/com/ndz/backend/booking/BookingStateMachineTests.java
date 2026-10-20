package com.ndz.backend.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ndz.backend.booking.internal.domain.BookingStatus;
import com.ndz.backend.shared.BusinessException;

/** Máy trạng thái booking (overview.md §7.7): mỗi chuyển hợp lệ, và mọi chuyển còn lại đều bị từ chối. */
class BookingStateMachineTests {

	private static final Map<BookingStatus, Set<BookingStatus>> VALID = Map.of(
			BookingStatus.PENDING_PAYMENT,
			EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.EXPIRED, BookingStatus.CANCELLED), BookingStatus.EXPIRED,
			EnumSet.of(BookingStatus.CONFIRMED), BookingStatus.CANCELLED, EnumSet.of(BookingStatus.CONFIRMED),
			BookingStatus.CONFIRMED, EnumSet.of(BookingStatus.REFUND_REQUESTED), BookingStatus.REFUND_REQUESTED,
			EnumSet.of(BookingStatus.REFUNDED, BookingStatus.REFUND_FAILED), BookingStatus.REFUND_FAILED,
			EnumSet.of(BookingStatus.REFUND_REQUESTED), BookingStatus.REFUNDED, EnumSet.noneOf(BookingStatus.class));

	@Test
	void everyTransitionInTheTableIsAllowed() {
		VALID.forEach((from, targets) -> targets.forEach(to -> {
			assertThat(from.canTransitionTo(to)).as("%s -> %s", from, to).isTrue();
			from.requireTransitionTo(to);
		}));
	}

	@Test
	void everyOtherTransitionIsRejectedWithConflict() {
		for (BookingStatus from : BookingStatus.values()) {
			for (BookingStatus to : BookingStatus.values()) {
				if (VALID.get(from).contains(to)) {
					continue;
				}
				assertThat(from.canTransitionTo(to)).as("%s -> %s", from, to).isFalse();
				assertThatThrownBy(() -> from.requireTransitionTo(to)).isInstanceOfSatisfying(BusinessException.class,
						e -> {
							assertThat(e.code()).isEqualTo("INVALID_BOOKING_TRANSITION");
							assertThat(e.status().value()).isEqualTo(409);
						});
			}
		}
	}

	@Test
	void refundedBookingsHaveNowhereToGo() {
		for (BookingStatus to : BookingStatus.values()) {
			assertThat(BookingStatus.REFUNDED.canTransitionTo(to)).isFalse();
		}
	}

}
