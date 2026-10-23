package com.ndz.payment.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.booking.api.BookingApi;
import com.ndz.booking.api.BookingApi.AdminTotals;
import com.ndz.catalog.api.CatalogApi;
import com.ndz.catalog.api.CatalogApi.OpeningSoonEvent;
import com.ndz.payment.dto.response.AdminDashboardResponses;
import com.ndz.payment.dto.response.AdminDashboardResponses.AdminDashboard;
import com.ndz.payment.dto.response.AdminDashboardResponses.Todo;
import com.ndz.payment.repository.ConnectedAccountRepository;
import com.ndz.payment.service.AdminDashboardService;

@Service
class AdminDashboardServiceImpl implements AdminDashboardService {

	private static final List<Integer> RANGES = List.of(7, 30, 90);
	private static final int OPENING_SOON_DAYS = 14;

	private final BookingApi bookings;
	private final CatalogApi catalog;
	private final ConnectedAccountRepository connectedAccounts;
	private final Clock clock;

	AdminDashboardServiceImpl(BookingApi bookings, CatalogApi catalog, ConnectedAccountRepository connectedAccounts,
			Clock clock) {
		this.bookings = bookings;
		this.catalog = catalog;
		this.connectedAccounts = connectedAccounts;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public AdminDashboard dashboard(int range) {
		int days = RANGES.contains(range) ? range : 30;
		Instant to = clock.instant();
		Instant from = to.minus(Duration.ofDays(days));
		AdminTotals totals = bookings.adminTotals(from, to);
		Todo todo = new Todo(catalog.countPendingOrganizers(), bookings.countNeedsReconcile(),
				bookings.countRefundFailed(), catalog.countSuspendedEvents(), connectedAccounts.countRestricted());
		List<AdminDashboardResponses.OpeningSoonEvent> openingSoon = catalog.openingSoonEvents(OPENING_SOON_DAYS)
				.stream().map(AdminDashboardServiceImpl::toOpeningSoon).toList();
		return new AdminDashboard(totals.grossMinor(), totals.feeMinor(), totals.ticketsSold(),
				catalog.countEventsOnSale(), todo, openingSoon);
	}

	private static AdminDashboardResponses.OpeningSoonEvent toOpeningSoon(OpeningSoonEvent event) {
		return new AdminDashboardResponses.OpeningSoonEvent(event.eventId(), event.title(), event.organizerId(),
				event.saleStartsAt());
	}

}
