package com.ndz.account.service.impl;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.account.client.KeycloakAdminClient;
import com.ndz.account.dto.response.AdminUserResponses.BookingItem;
import com.ndz.account.dto.response.AdminUserResponses.EventItem;
import com.ndz.account.dto.response.AdminUserResponses.LockResult;
import com.ndz.account.dto.response.AdminUserResponses.RefundItem;
import com.ndz.account.dto.response.AdminUserResponses.TicketItem;
import com.ndz.account.dto.response.AdminUserResponses.UserDetail;
import com.ndz.account.dto.response.AdminUserResponses.UserPage;
import com.ndz.account.entity.UserProfile;
import com.ndz.account.mapper.UserProfileMapper;
import com.ndz.account.repository.UserProfileRepository;
import com.ndz.account.service.AdminUserService;
import com.ndz.booking.api.BookingApi;
import com.ndz.catalog.api.CatalogApi;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.ticket.api.TicketApi;

/**
 * Quản lý người dùng của Admin (overview.md §6.6). Khoá / mở khoá gọi Keycloak trước rồi mới ghi hồ sơ, và không giữ
 * transaction trong lúc gọi ngoài: Keycloak lỗi thì hồ sơ chưa đổi, Admin thử lại được.
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {

	private static final Logger log = LoggerFactory.getLogger(AdminUserServiceImpl.class);

	private static final int MAX_PAGE_SIZE = 100;
	private static final int DETAIL_LIMIT = 50;
	private static final String ORGANIZER_ROLE = "ORGANIZER";
	private static final char LIKE_ESCAPE = '!';

	private final UserProfileRepository profiles;
	private final UserProfileMapper mapper;
	private final KeycloakAdminClient keycloak;
	private final BookingApi bookings;
	private final TicketApi tickets;
	private final CatalogApi catalog;
	private final Clock clock;

	AdminUserServiceImpl(UserProfileRepository profiles, UserProfileMapper mapper, KeycloakAdminClient keycloak,
			BookingApi bookings, TicketApi tickets, CatalogApi catalog, Clock clock) {
		this.profiles = profiles;
		this.mapper = mapper;
		this.keycloak = keycloak;
		this.bookings = bookings;
		this.tickets = tickets;
		this.catalog = catalog;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public UserPage list(String query, String role, int page, int size) {
		int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int pageNumber = Math.max(page, 0);
		Page<UserProfile> result = profiles.search(likePattern(query), blankToNull(role),
				PageRequest.of(pageNumber, pageSize));
		return new UserPage(result.getContent().stream().map(mapper::toUserItem).toList(), result.getTotalElements(),
				pageNumber, pageSize);
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetail detail(UUID userId) {
		UserProfile profile = find(userId);
		List<BookingApi.UserBooking> userBookings = bookings.bookingsOf(userId, DETAIL_LIMIT);
		List<BookingItem> bookingItems = userBookings.stream()
				.map(booking -> new BookingItem(booking.bookingId(), booking.eventTitle(), booking.startsAt(),
						booking.status(), booking.totalMinor(), booking.currency(), booking.createdAt(),
						booking.ticketCount()))
				.toList();
		List<RefundItem> refunds = userBookings.stream().filter(booking -> booking.refundStatus() != null)
				.map(booking -> new RefundItem(booking.bookingId(), booking.eventTitle(), booking.refundStatus(),
						booking.refundAmountMinor(), booking.currency()))
				.toList();
		List<TicketItem> ticketItems = tickets.ticketsOf(userId, DETAIL_LIMIT).stream()
				.map(ticket -> new TicketItem(ticket.ticketId(), ticket.eventTitle(), ticket.startsAt(),
						ticket.venueName(), ticket.sectionCode() + " · " + ticket.row() + ticket.number(),
						ticket.tierName(), ticket.status()))
				.toList();
		List<EventItem> events = profile.getRoles().contains(ORGANIZER_ROLE)
				? catalog.eventsOf(userId, DETAIL_LIMIT).stream().map(event -> new EventItem(event.eventId(),
						event.title(), event.status(), event.startsAt())).toList()
				: null;
		return new UserDetail(mapper.toUserItem(profile), bookingItems, ticketItems, refunds, events);
	}

	@Override
	public LockResult lock(UUID adminId, UUID userId) {
		if (adminId.equals(userId)) {
			throw new BusinessException(ErrorCode.CANNOT_LOCK_SELF, "Không thể khoá chính tài khoản của bạn");
		}
		return setEnabled(userId, false);
	}

	@Override
	public LockResult unlock(UUID userId) {
		return setEnabled(userId, true);
	}

	private LockResult setEnabled(UUID userId, boolean enabled) {
		find(userId);
		keycloak.setEnabled(userId, enabled);
		if (!enabled) {
			// Refresh token đã cấp mất hiệu lực; access token còn dùng được tới khi hết hạn (tối đa 5 phút).
			keycloak.logoutAllSessions(userId);
		}
		profiles.setEnabled(userId, enabled, clock.instant());
		log.info("Tài khoản {} {}", userId, enabled ? "được mở khoá" : "bị khoá");
		return new LockResult(enabled);
	}

	private UserProfile find(UUID userId) {
		return profiles.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
	}

	/** Mẫu {@code LIKE} viết thường, escape ký tự đặc biệt; rỗng nghĩa là không lọc. */
	private static String likePattern(String query) {
		String text = blankToNull(query);
		if (text == null) {
			return null;
		}
		StringBuilder escaped = new StringBuilder("%");
		for (char character : text.toLowerCase().toCharArray()) {
			if (character == LIKE_ESCAPE || character == '%' || character == '_') {
				escaped.append(LIKE_ESCAPE);
			}
			escaped.append(character);
		}
		return escaped.append('%').toString();
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

}
