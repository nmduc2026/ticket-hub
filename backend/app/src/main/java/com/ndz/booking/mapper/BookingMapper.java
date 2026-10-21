package com.ndz.booking.mapper;

import org.springframework.stereotype.Component;

import com.ndz.booking.dto.response.BookingResponses.BookingResponse;
import com.ndz.booking.dto.response.BookingResponses.HoldResponse;
import com.ndz.booking.dto.response.BookingResponses.Item;
import com.ndz.booking.dto.response.BookingResponses.SummaryResponse;
import com.ndz.booking.dto.response.BookingViews.BookingItemView;
import com.ndz.booking.dto.response.BookingViews.BookingSummary;
import com.ndz.booking.dto.response.BookingViews.BookingView;

@Component
public class BookingMapper {

	public HoldResponse toHold(BookingView booking) {
		return new HoldResponse(booking.id(), booking.expiresAt(), booking.totalMinor(), booking.currency());
	}

	public BookingResponse toBooking(BookingView booking) {
		return new BookingResponse(booking.id(), booking.eventId(), booking.status(), booking.expiresAt(),
				booking.totalMinor(), booking.currency(), booking.items().stream().map(this::toItem).toList());
	}

	public SummaryResponse toSummary(BookingSummary summary) {
		return new SummaryResponse(summary.bookingId(), summary.eventTitle(), summary.startsAt(), summary.status(),
				summary.totalMinor(), summary.currency(), summary.createdAt());
	}

	private Item toItem(BookingItemView item) {
		return new Item(item.seatId(), item.sectionCode(), item.rowLabel(), item.seatNumber(), item.tierId(),
				item.tierName(), item.priceMinor());
	}

}
