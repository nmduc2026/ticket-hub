package com.ndz.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Flyway baseline: đủ schema theo module, lịch sử migration và bảng event publication của Modulith.
 */
@IntegrationTest
class MigrationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void everyModuleHasItsOwnSchema() {
		List<String> schemas = jdbc.queryForList(
				"select schema_name from information_schema.schemata", String.class);

		assertThat(schemas).contains("account", "catalog", "booking", "payment", "ticket", "notification", "shared");
	}

	@Test
	void baselineIsAppliedAndRecordedInTheSharedSchema() {
		Integer applied = jdbc.queryForObject(
				"select count(*) from shared.flyway_schema_history where success and description = ?",
				Integer.class, "shared baseline");

		assertThat(applied).isEqualTo(1);
	}

	@Test
	void paymentSchemaHasItsTables() {
		List<String> tables = jdbc.queryForList(
				"select table_name from information_schema.tables where table_schema = 'payment'", String.class);

		assertThat(tables).contains("payments", "refunds", "stripe_events", "processed_events");
	}

	@Test
	void eventPublicationTableExistsInSharedSchema() {
		Integer tables = jdbc.queryForObject("""
				select count(*) from information_schema.tables
				where table_schema = 'shared' and table_name = 'event_publication'
				""", Integer.class);

		assertThat(tables).isEqualTo(1);
	}

}
