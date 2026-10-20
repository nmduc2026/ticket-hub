package com.ndz.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.backend.IntegrationTest;
import com.ndz.backend.TestTokens;

/**
 * Schema catalog (overview.md §6.1; BR-05, BR-06, BR-14): ràng buộc bất biến nằm ở DB và dữ liệu mẫu đúng như plan
 * Sprint 2 việc 1. Mỗi test chạy trong transaction rồi rollback nên không để lại dữ liệu.
 */
@IntegrationTest
@Transactional
class CatalogSchemaTests {

	private static final UUID SEAT_MAP = UUID.fromString("00000000-0000-7000-8000-0000000c0001");
	private static final UUID SECTION_A = UUID.fromString("00000000-0000-7000-8000-0000000d0001");
	private static final UUID SECTION_B = UUID.fromString("00000000-0000-7000-8000-0000000d0002");
	private static final UUID VENUE = UUID.fromString("00000000-0000-7000-8000-0000000b0001");
	private static final UUID HCM = UUID.fromString("00000000-0000-7000-8000-0000000a0001");
	private static final UUID WARD = UUID.fromString("00000000-0000-7000-8000-0000000a0101");

	@Autowired
	JdbcTemplate jdbc;

	// ---- Dữ liệu mẫu ----

	@Test
	void seedHasOneProvinceAndSeveralWards() {
		assertThat(count("select count(*) from catalog.administrative_units where level = 1")).isEqualTo(1);
		assertThat(count("select count(*) from catalog.administrative_units where level = 2 and parent_id = ?", HCM))
				.isGreaterThanOrEqualTo(3);
	}

	@Test
	void seedHasOneVenueWithAFiveHundredSeatMapInThreeSections() {
		assertThat(count("select count(*) from catalog.venues")).isEqualTo(1);
		assertThat(count("select count(*) from catalog.seat_maps where venue_id = ?", VENUE)).isEqualTo(1);
		assertThat(count("select count(*) from catalog.sections where seat_map_id = ?", SEAT_MAP)).isEqualTo(3);
		assertThat(count("select count(*) from catalog.seats s join catalog.sections c on c.id = s.section_id "
				+ "where c.seat_map_id = ?", SEAT_MAP)).isEqualTo(500);
	}

	@Test
	void seedOrganizerIsApprovedAndReadyForPayoutForTheOrganizerTestUser() {
		assertThat(jdbc.queryForObject(
				"select status || ':' || payout_ready from catalog.organizers where id = ?", String.class,
				TestTokens.ORGANIZER_ID)).isEqualTo("APPROVED:true");
	}

	@Test
	void seedFeePolicyIsFiveHundredBasisPoints() {
		assertThat(jdbc.queryForObject("select fee_bps from catalog.platform_fee_policies "
				+ "order by effective_from desc limit 1", Integer.class)).isEqualTo(500);
	}

	// ---- Đơn vị hành chính: CHECK cấp / loại / cha ----

	@Test
	void levelOneUnitMustNotHaveAParent() {
		assertViolates(() -> insertUnit("X1", "THANH_PHO", 1, HCM), "ck_admin_units_level_parent");
	}

	@Test
	void levelTwoUnitMustHaveAParent() {
		assertViolates(() -> insertUnit("X2", "PHUONG", 2, null), "ck_admin_units_level_parent");
	}

	@Test
	void levelOneUnitMustBeAProvinceOrCity() {
		assertViolates(() -> insertUnit("X3", "PHUONG", 1, null), "ck_admin_units_level_type");
	}

	@Test
	void levelTwoUnitMustBeAWardCommuneOrSpecialZone() {
		assertViolates(() -> insertUnit("X4", "TINH", 2, HCM), "ck_admin_units_level_type");
	}

	@Test
	void validProvinceAndWardAreAccepted() {
		insertUnit("OK1", "TINH", 1, null);
		insertUnit("OK2", "DAC_KHU", 2, HCM);

		assertThat(count("select count(*) from catalog.administrative_units where code in ('OK1', 'OK2')")).isEqualTo(2);
	}

	@Test
	void unitCodeIsUnique() {
		insertUnit("DUP", "TINH", 1, null);

		assertViolates(() -> insertUnit("DUP", "TINH", 1, null), "administrative_units_code_key");
	}

	// ---- tier_sections ----

	@Test
	void aSectionBelongsToAtMostOneTierPerEvent() {
		UUID event = insertDraftEvent();
		UUID tierOne = insertTier(event, "VIP");
		UUID tierTwo = insertTier(event, "Thường");
		assign(event, tierOne, SECTION_A);

		assertViolates(() -> assign(event, tierTwo, SECTION_A), "tier_sections_pkey");
	}

	@Test
	void theSameSectionCanHaveDifferentTiersInDifferentEvents() {
		UUID first = insertDraftEvent();
		UUID second = insertDraftEvent();

		assign(first, insertTier(first, "VIP"), SECTION_A);
		assign(second, insertTier(second, "Thường"), SECTION_A);

		assertThat(count("select count(*) from catalog.tier_sections where section_id = ?", SECTION_A)).isEqualTo(2);
	}

	@Test
	void aTierOfAnotherEventCannotBeAssigned() {
		UUID event = insertDraftEvent();
		UUID otherEvent = insertDraftEvent();
		UUID foreignTier = insertTier(otherEvent, "VIP");

		assertViolates(() -> assign(event, foreignTier, SECTION_B), "tier_sections_event_id_tier_id_fkey");
	}

	// ---- events: bất biến sau nháp (BR-05, BR-06, BR-14) ----

	@Test
	void draftEventMayBeIncomplete() {
		UUID event = insertDraftEvent();

		assertThat(jdbc.queryForObject("select status from catalog.events where id = ?", String.class, event))
				.isEqualTo("DRAFT");
	}

	@Test
	void publishedEventMustHaveVenueTimesAndLockedPlatformFee() {
		UUID event = insertDraftEvent();

		assertViolates(() -> jdbc.update("update catalog.events set status = 'PUBLISHED' where id = ?", event),
				"ck_events_complete_after_draft");
	}

	@Test
	void completeEventCanBePublished() {
		UUID event = insertDraftEvent();

		jdbc.update("""
				update catalog.events set status = 'PUBLISHED', venue_id = ?, seat_map_id = ?,
				    starts_at = now() + interval '30 days', ends_at = now() + interval '30 days 3 hours',
				    sale_starts_at = now() + interval '1 day', platform_fee_bps = 500 where id = ?""",
				VENUE, SEAT_MAP, event);

		assertThat(jdbc.queryForObject("select status from catalog.events where id = ?", String.class, event))
				.isEqualTo("PUBLISHED");
	}

	@Test
	void eventMustEndAfterItStarts() {
		UUID event = insertDraftEvent();

		assertViolates(() -> jdbc.update(
				"update catalog.events set starts_at = now() + interval '2 days', ends_at = now() + interval '1 day' "
						+ "where id = ?", event),
				"ck_events_times");
	}

	@Test
	void ticketPriceCannotBeNegative() {
		UUID event = insertDraftEvent();

		assertViolates(() -> jdbc.update(
				"insert into catalog.ticket_tiers (id, event_id, name, price_minor) values (?, ?, 'X', -1)",
				UUID.randomUUID(), event), "ticket_tiers_price_minor_check");
	}

	@Test
	void venueMustReferenceAnExistingWard() {
		assertViolates(() -> jdbc.update(
				"insert into catalog.venues (id, name, address, ward_id, capacity) values (?, 'V', 'A', ?, 10)",
				UUID.randomUUID(), UUID.randomUUID()), "venues_ward_id_fkey");
	}

	@Test
	void venueCapacityMustBePositive() {
		assertViolates(() -> jdbc.update(
				"insert into catalog.venues (id, name, address, ward_id, capacity) values (?, 'V', 'A', ?, 0)",
				UUID.randomUUID(), WARD), "venues_capacity_check");
	}

	// ---- helpers ----

	private void insertUnit(String code, String type, int level, UUID parent) {
		jdbc.update("insert into catalog.administrative_units (id, code, name, type, level, parent_id, effective_from) "
				+ "values (?, ?, ?, ?, ?, ?, '2025-07-01')", UUID.randomUUID(), code, code, type, level, parent);
	}

	private UUID insertDraftEvent() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into catalog.events (id, organizer_id, title) values (?, ?, 'Sự kiện thử')", id,
				TestTokens.ORGANIZER_ID);
		return id;
	}

	private UUID insertTier(UUID event, String name) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into catalog.ticket_tiers (id, event_id, name, price_minor) values (?, ?, ?, 500000)", id,
				event, name);
		return id;
	}

	private void assign(UUID event, UUID tier, UUID section) {
		jdbc.update("insert into catalog.tier_sections (event_id, tier_id, section_id) values (?, ?, ?)", event, tier,
				section);
	}

	private int count(String sql, Object... args) {
		Integer value = jdbc.queryForObject(sql, Integer.class, args);
		return value == null ? 0 : value;
	}

	private static void assertViolates(Runnable statement, String constraint) {
		assertThatThrownBy(statement::run).isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining(constraint);
	}

}
