package com.ndz.booking.repository;

import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.selectCount;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.springframework.stereotype.Repository;

import com.ndz.booking.dto.request.OrganizerBookingFilter;
import com.ndz.booking.dto.response.SalesResponses.DaySales;
import com.ndz.booking.dto.response.SalesResponses.OrganizerBooking;
import com.ndz.booking.dto.response.SalesResponses.TierSales;

/**
 * Truy vấn báo cáo của organizer bằng jOOQ (overview.md §6.2, skill springbot mục 5): chỉ đọc bảng của Booking. Chưa
 * sinh code từ schema nên bảng / cột gọi theo tên; mọi giá trị đi qua tham số bind.
 */
@Repository
public class OrganizerSalesQueries {

	/** Ngày theo giờ Việt Nam của mốc xác nhận đơn (doanh thu theo ngày). */
	private static final Field<LocalDate> CONFIRMED_DAY = field(
			"(({0} AT TIME ZONE 'Asia/Ho_Chi_Minh')::date)", LocalDate.class, field(name("b", "confirmed_at")));

	private static final Table<?> SALES = table(name("booking", "sales_by_event")).as("s");
	private static final Table<?> INVENTORY = table(name("booking", "seat_inventory")).as("i");
	private static final Table<?> BOOKINGS = table(name("booking", "bookings")).as("b");
	private static final Table<?> ITEMS = table(name("booking", "booking_items")).as("bi");
	private static final Table<?> SNAPSHOTS = table(name("booking", "event_snapshots")).as("e");

	private final DSLContext dsl;

	OrganizerSalesQueries(DSLContext dsl) {
		this.dsl = dsl;
	}

	/** Số liệu theo hạng vé của sự kiện: sức chứa từ tồn kho ghế, bán / hoàn / check-in từ {@code sales_by_event}. */
	public List<TierSales> byTier(UUID eventId) {
		Field<UUID> tierId = field(name("i", "tier_id"), UUID.class);
		Field<String> tierName = field(name("i", "tier_name"), String.class);
		return dsl.select(tierId, tierName, count().as("capacity"))
				.select(intField("sold_count"), intField("refunded_count"), longField("revenue_minor"),
						longField("fee_minor"), longField("refunded_minor"), intField("checked_in_count"))
				.from(INVENTORY)
				.leftJoin(SALES)
				.on(field(name("s", "event_id")).eq(field(name("i", "event_id")))
						.and(field(name("s", "tier_id")).eq(tierId)))
				.where(field(name("i", "event_id"), UUID.class).eq(eventId))
				.groupBy(tierId, tierName, field(name("s", "sold_count")), field(name("s", "refunded_count")),
						field(name("s", "revenue_minor")), field(name("s", "fee_minor")),
						field(name("s", "refunded_minor")), field(name("s", "checked_in_count")))
				.orderBy(tierName)
				.fetch(row -> new TierSales(row.get(tierId), row.get(tierName), orZero(row.get("sold_count")),
						orZero(row.get("refunded_count")), row.get("capacity", Integer.class),
						asLong(row.get("revenue_minor")), asLong(row.get("fee_minor")),
						asLong(row.get("refunded_minor")), orZero(row.get("checked_in_count"))));
	}

	/** Phí của phần đã hoàn (để tính ròng), cộng mọi hạng. */
	public long refundedFee(UUID eventId) {
		BigDecimal total = dsl.select(sum(field(name("s", "refunded_fee_minor"), Long.class))).from(SALES)
				.where(field(name("s", "event_id"), UUID.class).eq(eventId)).fetchOne(0, BigDecimal.class);
		return total == null ? 0 : total.longValue();
	}

	/** Doanh thu gộp và số vé theo ngày xác nhận (giờ Việt Nam) của các sự kiện, trong [from, to). */
	public List<DaySales> byDay(Collection<UUID> eventIds, Instant from, Instant to) {
		Field<BigDecimal> gross = sum(field(name("b", "total_minor"), Long.class)).as("gross");
		Field<Integer> tickets = field(selectCount().from(ITEMS)
				.where(field(name("bi", "booking_id")).eq(field(name("b", "id"))))).as("tickets");
		Table<?> perBooking = dsl.select(CONFIRMED_DAY.as("day"), field(name("b", "total_minor"), Long.class).as("total"),
				tickets)
				.from(BOOKINGS)
				.where(field(name("b", "event_id"), UUID.class).in(eventIds))
				.and(confirmedBetween(from, to))
				.asTable("d");
		Field<LocalDate> day = field(name("d", "day"), LocalDate.class);
		return dsl.select(day, sum(field(name("d", "total"), Long.class)), sum(field(name("d", "tickets"), Integer.class)))
				.from(perBooking).groupBy(day).orderBy(day)
				.fetch(row -> new DaySales(row.value1(), row.value2().longValue(), row.value3().intValue()));
	}

	/** Tổng của các đơn đã xác nhận trong khoảng: gộp, phí, phần đã hoàn và phí của phần đó, số vé còn hiệu lực. */
	public Totals totals(Collection<UUID> eventIds, Instant from, Instant to) {
		Field<Long> total = field(name("b", "total_minor"), Long.class);
		Field<Long> fee = field("floor({0} * {1} / 10000)", Long.class, total, field(name("e", "platform_fee_bps")));
		Condition refunded = field(name("b", "status"), String.class).eq("REFUNDED");
		Field<Integer> tickets = field(selectCount().from(ITEMS)
				.where(field(name("bi", "booking_id")).eq(field(name("b", "id")))));
		Record row = dsl.select(sum(total).as("gross"), sum(fee).as("fee"),
				sum(when(refunded, total).otherwise(0L)).as("refunded"),
				sum(when(refunded, fee).otherwise(0L)).as("refunded_fee"),
				sum(when(refunded, 0).otherwise(tickets)).as("tickets"))
				.from(BOOKINGS).join(SNAPSHOTS).on(field(name("e", "event_id")).eq(field(name("b", "event_id"))))
				.where(field(name("b", "event_id"), UUID.class).in(eventIds))
				.and(confirmedBetween(from, to))
				.fetchOne();
		return new Totals(asLong(row.get("gross")), asLong(row.get("fee")), asLong(row.get("refunded")),
				asLong(row.get("refunded_fee")), (int) asLong(row.get("tickets")));
	}

	/** Đơn của các sự kiện theo bộ lọc, mới tạo trước. */
	public List<OrganizerBooking> bookings(Collection<UUID> eventIds, OrganizerBookingFilter filter, int offset, int limit) {
		return dsl.select(field(name("b", "id"), UUID.class), field(name("b", "event_id"), UUID.class),
				field(name("e", "title"), String.class), field(name("b", "status"), String.class),
				field(selectCount().from(ITEMS).where(field(name("bi", "booking_id")).eq(field(name("b", "id")))))
						.as("ticket_count"),
				field(name("b", "total_minor"), Long.class), field(name("b", "currency"), String.class),
				field(name("b", "contact_email"), String.class), field(name("b", "confirmed_at"), OffsetDateTime.class),
				field(name("b", "created_at"), OffsetDateTime.class))
				.from(BOOKINGS).join(SNAPSHOTS).on(field(name("e", "event_id")).eq(field(name("b", "event_id"))))
				.where(matches(eventIds, filter))
				.orderBy(field(name("b", "created_at")).desc(), field(name("b", "id")).desc())
				.offset(offset).limit(limit)
				.fetch(row -> new OrganizerBooking(row.value1(), row.value2(), row.value3(), row.value4(),
						row.get("ticket_count", Integer.class), row.value6(), row.value7(), row.value8(),
						row.value9() == null ? null : row.value9().toInstant(), row.value10().toInstant()));
	}

	public long countBookings(Collection<UUID> eventIds, OrganizerBookingFilter filter) {
		return dsl.selectCount().from(BOOKINGS).where(matches(eventIds, filter)).fetchOne(0, Long.class);
	}

	/** Sự kiện của organizer (theo bản sao), dùng để giới hạn mọi truy vấn trong sự kiện của chính họ. */
	public List<UUID> eventIdsOf(UUID organizerId) {
		return dsl.select(field(name("e", "event_id"), UUID.class)).from(SNAPSHOTS)
				.where(field(name("e", "organizer_id"), UUID.class).eq(organizerId)).fetch(0, UUID.class);
	}


	public record Totals(long grossMinor, long feeMinor, long refundedMinor, long refundedFeeMinor, int tickets) {
	}

	private static Condition matches(Collection<UUID> eventIds, OrganizerBookingFilter filter) {
		Condition condition = field(name("b", "event_id"), UUID.class).in(eventIds);
		if (filter.eventId() != null) {
			condition = condition.and(field(name("b", "event_id"), UUID.class).eq(filter.eventId()));
		}
		if (filter.statuses() != null && !filter.statuses().isEmpty()) {
			condition = condition.and(field(name("b", "status"), String.class).in(filter.statuses()));
		}
		if (filter.from() != null) {
			condition = condition.and(field(name("b", "created_at"), OffsetDateTime.class).ge(utc(filter.from())));
		}
		if (filter.to() != null) {
			condition = condition.and(field(name("b", "created_at"), OffsetDateTime.class).lt(utc(filter.to())));
		}
		if (filter.q() != null && !filter.q().isBlank()) {
			String q = filter.q().strip().toLowerCase();
			condition = condition.and(field(name("b", "contact_email"), String.class).lower().contains(q)
					.or(field("{0}::text", String.class, field(name("b", "id"))).startsWith(q)));
		}
		return condition;
	}

	private static Condition confirmedBetween(Instant from, Instant to) {
		Field<OffsetDateTime> confirmedAt = field(name("b", "confirmed_at"), OffsetDateTime.class);
		return (from == null ? noCondition() : confirmedAt.ge(utc(from)))
				.and(to == null ? confirmedAt.isNotNull() : confirmedAt.lt(utc(to)));
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static Field<Integer> intField(String column) {
		return field(name("s", column), Integer.class).as(column);
	}

	private static Field<Long> longField(String column) {
		return field(name("s", column), Long.class).as(column);
	}

	private static int orZero(Object value) {
		return value == null ? 0 : ((Number) value).intValue();
	}

	private static long asLong(Object value) {
		return value == null ? 0 : ((Number) value).longValue();
	}

}
