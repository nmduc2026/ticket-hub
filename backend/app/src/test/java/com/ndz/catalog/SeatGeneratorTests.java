package com.ndz.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.ndz.catalog.util.SeatGenerator;
import com.ndz.catalog.util.SeatGenerator.GeneratedSeat;
import com.ndz.catalog.util.SeatGenerator.GridParams;

/**
 * {@code SeatGenerator} (ADR 0030, spike hạng mục 16): ra cùng toạ độ với {@code generateSeats} ở frontend, vì hai bên
 * đọc chung {@code shared-test-data/generate-seats.json}. Test thuần, không cần Spring.
 */
class SeatGeneratorTests {

	private static final double EPSILON = 1e-9;

	record Case(String name, GridParams params, List<GeneratedSeat> expected) {
		@Override
		public String toString() {
			return name;
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	@DisplayName("Cùng tham số → cùng toạ độ với bản TypeScript")
	void sameCoordinatesAsFrontend(Case item) {
		List<GeneratedSeat> actual = SeatGenerator.generate(item.params());

		assertThat(actual).hasSameSizeAs(item.expected());
		for (int i = 0; i < actual.size(); i++) {
			GeneratedSeat seat = actual.get(i);
			GeneratedSeat expected = item.expected().get(i);
			assertThat(seat.rowLabel()).isEqualTo(expected.rowLabel());
			assertThat(seat.seatNumber()).isEqualTo(expected.seatNumber());
			assertThat(seat.x()).isCloseTo(expected.x(), org.assertj.core.data.Offset.offset(EPSILON));
			assertThat(seat.y()).isCloseTo(expected.y(), org.assertj.core.data.Offset.offset(EPSILON));
		}
	}

	@Test
	@DisplayName("Nhãn hàng đi A…Z rồi AA, AB")
	void rowLabels() {
		assertThat(Stream.of(0, 25, 26, 27, 701, 702).map(SeatGenerator::rowLabel).toList())
				.containsExactly("A", "Z", "AA", "AB", "ZZ", "AAA");
	}

	static Stream<Case> cases() throws IOException {
		JsonNode root = new JsonMapper().readTree(Files.readString(fixture()));
		List<Case> cases = new ArrayList<>();
		for (JsonNode node : root.get("cases")) {
			JsonNode p = node.get("params");
			List<String> hidden = new ArrayList<>();
			p.get("hiddenSeats").forEach(seat -> hidden.add(seat.asString()));
			GridParams params = new GridParams(p.get("x").asDouble(), p.get("y").asDouble(), p.get("rotation").asDouble(),
					p.get("rows").asInt(), p.get("seatsPerRow").asInt(), p.get("rowGap").asDouble(),
					p.get("seatGap").asDouble(), p.get("curve").asDouble(), "RTL".equals(p.get("numbering").asString()), hidden);
			List<GeneratedSeat> expected = new ArrayList<>();
			for (JsonNode seat : node.get("expected")) {
				expected.add(new GeneratedSeat(seat.get("rowLabel").asString(), seat.get("seatNumber").asInt(),
						seat.get("x").asDouble(), seat.get("y").asDouble()));
			}
			cases.add(new Case(node.get("name").asString(), params, expected));
		}
		return cases.stream();
	}

	/** Tìm từ thư mục chạy test ngược lên gốc repo, để chạy được từ {@code backend/} hoặc {@code backend/app/}. */
	private static Path fixture() {
		Path dir = Path.of("").toAbsolutePath();
		while (dir != null) {
			Path candidate = dir.resolve("shared-test-data/generate-seats.json");
			if (Files.exists(candidate)) {
				return candidate;
			}
			dir = dir.getParent();
		}
		throw new IllegalStateException("Không thấy shared-test-data/generate-seats.json");
	}

}
