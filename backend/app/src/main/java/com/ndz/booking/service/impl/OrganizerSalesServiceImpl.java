package com.ndz.booking.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.booking.dto.request.OrganizerBookingFilter;
import com.ndz.booking.dto.response.SalesResponses.EventSales;
import com.ndz.booking.dto.response.SalesResponses.OrganizerBooking;
import com.ndz.booking.dto.response.SalesResponses.OrganizerBookingPage;
import com.ndz.booking.dto.response.SalesResponses.OrganizerDashboard;
import com.ndz.booking.dto.response.SalesResponses.TierSales;
import com.ndz.booking.repository.OrganizerSalesQueries;
import com.ndz.booking.repository.OrganizerSalesQueries.Totals;
import com.ndz.booking.service.OrganizerSalesService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.export.CsvExporter;

@Service
public class OrganizerSalesServiceImpl implements OrganizerSalesService {

	private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final String CURRENCY = "VND";
	private static final int MAX_PAGE_SIZE = 100;
	private static final int RECENT_BOOKINGS = 5;
	private static final List<Integer> RANGES = List.of(7, 30, 90);

	private final OrganizerSalesQueries queries;
	private final CsvExporter csv;
	private final Clock clock;

	OrganizerSalesServiceImpl(OrganizerSalesQueries queries, CsvExporter csv, Clock clock) {
		this.queries = queries;
		this.csv = csv;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public EventSales eventSales(UUID organizerId, UUID eventId) {
		if (!queries.eventIdsOf(organizerId).contains(eventId)) {
			throw new BusinessException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện");
		}
		List<TierSales> tiers = queries.byTier(eventId);
		long gross = tiers.stream().mapToLong(TierSales::grossMinor).sum();
		long fee = tiers.stream().mapToLong(TierSales::feeMinor).sum();
		long refunded = tiers.stream().mapToLong(TierSales::refundedMinor).sum();
		long net = net(gross, fee, refunded, queries.refundedFee(eventId));
		return new EventSales(eventId, CURRENCY, gross, fee, refunded, net,
				tiers.stream().mapToInt(tier -> tier.sold() - tier.refunded()).sum(),
				tiers.stream().mapToInt(TierSales::capacity).sum(), tiers.stream().mapToInt(TierSales::checkedIn).sum(),
				tiers, queries.byDay(List.of(eventId), null, null));
	}

	@Override
	@Transactional(readOnly = true)
	public OrganizerDashboard dashboard(UUID organizerId, int days) {
		int range = RANGES.contains(days) ? days : 30;
		LocalDate to = LocalDate.ofInstant(clock.instant(), VIETNAM);
		LocalDate from = to.minusDays(range - 1L);
		List<UUID> events = queries.eventIdsOf(organizerId);
		if (events.isEmpty()) {
			return new OrganizerDashboard(from, to, CURRENCY, 0, 0, 0, 0, 0, List.of(), List.of());
		}
		Instant start = from.atStartOfDay(VIETNAM).toInstant();
		Instant end = to.plusDays(1).atStartOfDay(VIETNAM).toInstant();
		Totals totals = queries.totals(events, start, end);
		List<OrganizerBooking> recent = queries.bookings(events, OrganizerBookingFilter.none(), 0,
				RECENT_BOOKINGS);
		return new OrganizerDashboard(from, to, CURRENCY, totals.grossMinor(), totals.feeMinor(),
				totals.refundedMinor(),
				net(totals.grossMinor(), totals.feeMinor(), totals.refundedMinor(), totals.refundedFeeMinor()),
				totals.tickets(), queries.byDay(events, start, end), recent);
	}

	@Override
	@Transactional(readOnly = true)
	public OrganizerBookingPage bookings(UUID organizerId, OrganizerBookingFilter filter, int page, int size) {
		List<UUID> events = ownedEvents(organizerId, filter);
		int limit = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int offset = Math.max(page, 0) * limit;
		if (events.isEmpty()) {
			return new OrganizerBookingPage(List.of(), 0, Math.max(page, 0), limit);
		}
		return new OrganizerBookingPage(queries.bookings(events, filter, offset, limit),
				queries.countBookings(events, filter), Math.max(page, 0), limit);
	}

	@Override
	@Transactional
	public String exportBookings(UUID organizerId, OrganizerBookingFilter filter) {
		List<UUID> events = ownedEvents(organizerId, filter);
		long total = events.isEmpty() ? 0 : queries.countBookings(events, filter);
		csv.requireWithinLimit(total);
		List<OrganizerBooking> rows = events.isEmpty() ? List.of() : queries.bookings(events, filter, 0, (int) total);
		Map<String, Object> filters = new HashMap<>();
		filters.put("eventId", filter.eventId());
		filters.put("statuses", filter.statuses());
		filters.put("from", filter.from());
		filters.put("to", filter.to());
		filters.put("q", filter.q());
		filters.values().removeIf(java.util.Objects::isNull);
		return csv.export(organizerId, "organizer_bookings", filters,
				List.of("Mã đơn", "Sự kiện", "Trạng thái", "Số vé", "Tổng tiền (VND)", "Email", "Xác nhận lúc", "Tạo lúc"),
				rows.stream().<List<?>>map(row -> List.of(row.bookingId(), row.eventTitle(), row.status(),
						row.ticketCount(), row.totalMinor(), row.contactEmail(),
						row.confirmedAt() == null ? "" : row.confirmedAt().atZone(VIETNAM).toLocalDateTime(),
						row.createdAt().atZone(VIETNAM).toLocalDateTime())).toList());
	}

	/** Sự kiện của organizer, thu hẹp về {@code filter.eventId} nếu có; sự kiện của người khác → 404. */
	private List<UUID> ownedEvents(UUID organizerId, OrganizerBookingFilter filter) {
		List<UUID> events = queries.eventIdsOf(organizerId);
		if (filter.eventId() == null) {
			return events;
		}
		if (!events.contains(filter.eventId())) {
			throw new BusinessException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện");
		}
		return List.of(filter.eventId());
	}

	/** Ròng của organizer = (gộp − đã hoàn) − (phí − phí của phần đã hoàn) (overview.md §6.2). */
	private static long net(long gross, long fee, long refunded, long refundedFee) {
		return (gross - refunded) - (fee - refundedFee);
	}

}
