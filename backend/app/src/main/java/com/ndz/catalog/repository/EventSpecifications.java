package com.ndz.catalog.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.ndz.catalog.entity.Event;
import com.ndz.catalog.entity.enums.EventStatus;

/** Điều kiện lọc danh sách sự kiện của organizer; mỗi hàm là một điều kiện độc lập để ghép bằng {@code and}. */
public final class EventSpecifications {

	private static final char LIKE_ESCAPE = '\\';

	private EventSpecifications() {
	}

	public static Specification<Event> ownedBy(UUID organizerId) {
		return (root, query, builder) -> builder.equal(root.get("organizerId"), organizerId);
	}

	public static Specification<Event> hasStatusIn(Collection<EventStatus> statuses) {
		return (root, query, builder) -> root.get("status").in(statuses);
	}

	public static Specification<Event> atVenue(UUID venueId) {
		return (root, query, builder) -> builder.equal(root.get("venueId"), venueId);
	}

	public static Specification<Event> hasStatus(EventStatus status) {
		return (root, query, builder) -> builder.equal(root.get("status"), status);
	}

	public static Specification<Event> startsAtFrom(Instant from) {
		return (root, query, builder) -> builder.greaterThanOrEqualTo(root.get("startsAt"), from);
	}

	public static Specification<Event> startsAtTo(Instant to) {
		return (root, query, builder) -> builder.lessThan(root.get("startsAt"), to);
	}

	/** Tìm theo một đoạn của tiêu đề, không phân biệt hoa thường; ký tự đặc biệt của LIKE được coi là chữ thường. */
	public static Specification<Event> titleContains(String text) {
		String pattern = "%" + escapeLike(text.toLowerCase()) + "%";
		return (root, query, builder) -> builder.like(builder.lower(root.get("title")), pattern, LIKE_ESCAPE);
	}

	private static String escapeLike(String text) {
		StringBuilder escaped = new StringBuilder();
		for (char character : text.toCharArray()) {
			if (character == LIKE_ESCAPE || character == '%' || character == '_') {
				escaped.append(LIKE_ESCAPE);
			}
			escaped.append(character);
		}
		return escaped.toString();
	}

}
