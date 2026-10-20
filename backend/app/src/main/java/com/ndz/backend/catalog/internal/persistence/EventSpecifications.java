package com.ndz.backend.catalog.internal.persistence;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.ndz.backend.catalog.internal.domain.Event;
import com.ndz.backend.catalog.internal.domain.EventStatus;

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
