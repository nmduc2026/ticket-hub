package com.ndz.catalog.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.catalog.client.SeatMapLock;
import com.ndz.catalog.constant.ImportLimits;
import com.ndz.catalog.dto.response.CatalogViews.SeatMapRow;
import com.ndz.catalog.dto.response.ImportResponses.ImportContext;
import com.ndz.catalog.dto.response.ImportResponses.ImportOutcome;
import com.ndz.catalog.dto.response.ImportResponses.RowError;
import com.ndz.catalog.entity.enums.DuplicateMode;
import com.ndz.catalog.entity.enums.ImportType;
import com.ndz.catalog.service.ImportHandler;
import com.ndz.catalog.repository.SeatMapAdminRepository;
import com.ndz.catalog.repository.SeatMapAdminRepository.SeatRow;
import com.ndz.catalog.repository.SeatMapAdminRepository.StoredSection;
import com.ndz.catalog.util.ExcelFiles.GuideLine;
import com.ndz.catalog.util.ExcelFiles.ImportRow;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.Uuid7;

/**
 * Import ghế vào một sơ đồ có sẵn thành khu {@code IMPORTED} (ADR 0030, docs/design/README.md §7). Mỗi dòng là một đoạn
 * ghế liền nhau của một hàng; khoá ghế là {@code ma_khu + hang + số ghế}. Khu đã có kiểu {@code GRID} không bị nhập đè.
 */
@Component
class SeatMapImportHandlerImpl implements ImportHandler {

	private static final List<String> COLUMNS = List.of("ma_khu", "ten_khu", "hang", "ghe_tu", "ghe_den", "x", "y");
	private static final String IMPORTED_SHAPE = "{\"kind\":\"IMPORTED\"}";
	/** Khoảng cách ghế và hàng khi file không cho toạ độ, cùng đơn vị với toạ độ sơ đồ. */
	private static final int SEAT_STEP = 28;
	private static final int ROW_STEP = 30;
	private static final int ORIGIN = 40;
	private static final int MAX_SEATS_PER_ROW_SEGMENT = 1_000;

	/** Một dòng hợp lệ: ghế từ {@code from} đến {@code to} của một hàng, bắt đầu ở (x, y). */
	private record Segment(ImportRow row, String sectionCode, String sectionName, String rowLabel, int from, int to,
			int x, int y) {
	}

	private final SeatMapAdminRepository maps;
	private final SeatMapLock lock;
	private final TransactionTemplate tx;

	SeatMapImportHandlerImpl(SeatMapAdminRepository maps, SeatMapLock lock, TransactionTemplate tx) {
		this.maps = maps;
		this.lock = lock;
		this.tx = tx;
	}

	@Override
	public ImportType type() {
		return ImportType.SEAT_MAPS;
	}

	@Override
	public List<String> columns() {
		return COLUMNS;
	}

	@Override
	public Set<String> textColumns() {
		return Set.of("ma_khu", "hang");
	}

	@Override
	public List<List<String>> examples() {
		return List.of(List.of("A", "Khu A", "A", "1", "10", "", ""), List.of("A", "Khu A", "B", "1", "10", "", ""));
	}

	@Override
	public List<GuideLine> guide() {
		return List.of(new GuideLine("ma_khu", true, "Mã khu trong sơ đồ; khu chưa có sẽ được tạo thành khu IMPORTED"),
				new GuideLine("ten_khu", true, "Tên khu, cùng một mã khu thì phải cùng tên"),
				new GuideLine("hang", true, "Nhãn hàng, ví dụ A, B, AA"),
				new GuideLine("ghe_tu", true, "Số ghế đầu của đoạn, số nguyên từ 1"),
				new GuideLine("ghe_den", true, "Số ghế cuối của đoạn, không nhỏ hơn ghe_tu"),
				new GuideLine("x", false, "Toạ độ ngang của ghế đầu đoạn; để trống thì tự xếp từ mép trái"),
				new GuideLine("y", false, "Toạ độ dọc của hàng; để trống thì xếp theo thứ tự hàng xuất hiện trong khu"));
	}

	@Override
	public void prepare(ImportContext context) {
		if (context.seatMapId() == null) {
			throw new BusinessException(ErrorCode.SEAT_MAP_REQUIRED, "Cần chọn sơ đồ ghế để import vào");
		}
		SeatMapRow map = maps.find(context.seatMapId())
				.orElseThrow(() -> new BusinessException(ErrorCode.SEAT_MAP_NOT_FOUND, "Không tìm thấy sơ đồ ghế"));
		if (map.locked()) {
			throw new BusinessException(ErrorCode.SEAT_MAP_LOCKED,
					"Sơ đồ đã có sự kiện publish dùng, hãy tạo bản sao để sửa");
		}
	}

	@Override
	public ImportOutcome run(List<ImportRow> rows, ImportContext context) {
		List<RowError> errors = new ArrayList<>();
		List<Segment> segments = validate(rows, errors);
		if (context.dryRun()) {
			return outcome(tx.execute(status -> apply(segments, context, errors)), errors);
		}
		String token = lock.tryAcquire(context.seatMapId());
		if (token == null) {
			throw new BusinessException(ErrorCode.SEAT_MAP_BEING_EDITED, "Admin khác đang lưu sơ đồ này, thử lại sau");
		}
		try {
			// Kiểm lại sau khi có khoá: sơ đồ có thể vừa bị khoá bởi lần publish khác.
			prepare(context);
			return outcome(tx.execute(status -> apply(segments, context, errors)), errors);
		}
		finally {
			lock.release(context.seatMapId(), token);
		}
	}

	private List<Segment> validate(List<ImportRow> rows, List<RowError> errors) {
		Map<String, String> sectionNames = new HashMap<>();
		Map<String, Integer> sectionRowCount = new HashMap<>();
		Map<String, Integer> rowIndex = new HashMap<>();
		Map<String, Integer> seatOwner = new HashMap<>();
		List<Segment> segments = new ArrayList<>();
		int totalSeats = 0;
		for (ImportRow row : rows) {
			String code = row.get("ma_khu");
			String name = row.get("ten_khu");
			String rowLabel = row.get("hang");
			boolean ok = true;
			if (code.isEmpty()) {
				errors.add(new RowError(row.number(), "ma_khu", "Thiếu mã khu"));
				ok = false;
			}
			if (name.isEmpty()) {
				errors.add(new RowError(row.number(), "ten_khu", "Thiếu tên khu"));
				ok = false;
			}
			else if (!code.isEmpty()) {
				String known = sectionNames.putIfAbsent(code, name);
				if (known != null && !known.equals(name)) {
					errors.add(new RowError(row.number(), "ten_khu", "Cùng mã khu phải cùng tên, đã có \"" + known + "\""));
					ok = false;
				}
			}
			if (rowLabel.isEmpty()) {
				errors.add(new RowError(row.number(), "hang", "Thiếu hàng"));
				ok = false;
			}
			int from = parseInt(row.get("ghe_tu"));
			int to = parseInt(row.get("ghe_den"));
			if (row.get("ghe_tu").isEmpty()) {
				errors.add(new RowError(row.number(), "ghe_tu", "Thiếu số ghế đầu"));
				ok = false;
			}
			else if (from < 1) {
				errors.add(new RowError(row.number(), "ghe_tu", "Số ghế đầu phải là số nguyên từ 1"));
				ok = false;
			}
			if (row.get("ghe_den").isEmpty()) {
				errors.add(new RowError(row.number(), "ghe_den", "Thiếu số ghế cuối"));
				ok = false;
			}
			else if (to < 1) {
				errors.add(new RowError(row.number(), "ghe_den", "Số ghế cuối phải là số nguyên từ 1"));
				ok = false;
			}
			else if (from >= 1 && to < from) {
				errors.add(new RowError(row.number(), "ghe_den", "ghe_den phải không nhỏ hơn ghe_tu"));
				ok = false;
			}
			else if (from >= 1 && to - from + 1 > MAX_SEATS_PER_ROW_SEGMENT) {
				errors.add(new RowError(row.number(), "ghe_den", "Một dòng tối đa " + MAX_SEATS_PER_ROW_SEGMENT + " ghế"));
				ok = false;
			}
			Integer x = optionalInt(row, "x", errors);
			Integer y = optionalInt(row, "y", errors);
			if (x != null && x == Integer.MIN_VALUE || y != null && y == Integer.MIN_VALUE) {
				ok = false;
			}
			if (!ok) {
				continue;
			}
			// Trùng ghế với dòng khác: báo ở dòng sau, dòng trước vẫn giữ.
			String duplicate = null;
			for (int n = from; n <= to && duplicate == null; n++) {
				Integer owner = seatOwner.get(code + "|" + rowLabel + "|" + n);
				if (owner != null) {
					duplicate = "Trùng ghế " + rowLabel + n + " với dòng " + owner;
				}
			}
			if (duplicate != null) {
				errors.add(new RowError(row.number(), "ghe_tu", duplicate));
				continue;
			}
			int count = to - from + 1;
			if (totalSeats + count > ImportLimits.MAX_SEATS_PER_IMPORT) {
				errors.add(new RowError(row.number(), "ghe_den", "Vượt " + ImportLimits.MAX_SEATS_PER_IMPORT + " ghế mỗi lần import"));
				continue;
			}
			totalSeats += count;
			for (int n = from; n <= to; n++) {
				seatOwner.put(code + "|" + rowLabel + "|" + n, row.number());
			}
			String rowKey = code + "|" + rowLabel;
			int index = rowIndex.computeIfAbsent(rowKey, key -> sectionRowCount.merge(code, 1, Integer::sum) - 1);
			int startX = x != null ? x : ORIGIN + (from - 1) * SEAT_STEP;
			int startY = y != null ? y : ORIGIN + index * ROW_STEP;
			segments.add(new Segment(row, code, name, rowLabel, from, to, startX, startY));
		}
		return segments;
	}

	/** Trả {tạo mới, cập nhật, bỏ qua} tính theo dòng; ghi khi không phải dry-run. */
	private int[] apply(List<Segment> segments, ImportContext context, List<RowError> errors) {
		Map<String, StoredSection> existing = maps.sectionsByCode(context.seatMapId());
		Map<String, List<Segment>> byFileSection = new LinkedHashMap<>();
		for (Segment segment : segments) {
			StoredSection stored = existing.get(segment.sectionCode());
			if (stored != null && !stored.shapeJson().contains("\"IMPORTED\"")) {
				errors.add(new RowError(segment.row().number(), "ma_khu",
						"Khu " + segment.sectionCode() + " đã có và là khu lưới, không nhập đè được"));
				continue;
			}
			byFileSection.computeIfAbsent(segment.sectionCode(), key -> new ArrayList<>()).add(segment);
		}
		int created = 0;
		int updated = 0;
		int skipped = 0;
		for (Map.Entry<String, List<Segment>> entry : byFileSection.entrySet()) {
			StoredSection stored = existing.get(entry.getKey());
			UUID sectionId = stored != null ? stored.id() : Uuid7.next();
			String name = entry.getValue().get(0).sectionName();
			if (!context.dryRun()) {
				if (stored == null) {
					maps.insertSection(sectionId, context.seatMapId(), entry.getKey(), name, IMPORTED_SHAPE);
				}
				else if (context.onDuplicate() == DuplicateMode.UPDATE) {
					maps.updateSection(sectionId, name, stored.shapeJson());
				}
			}
			Set<String> existingSeats = new HashSet<>();
			if (stored != null) {
				for (SeatRow seat : maps.seatsOf(stored.id())) {
					existingSeats.add(seat.rowLabel() + "|" + seat.seatNumber());
				}
			}
			List<SeatRow> toInsert = new ArrayList<>();
			List<SeatRow> toUpdate = new ArrayList<>();
			for (Segment segment : entry.getValue()) {
				boolean anyNew = false;
				boolean anyExisting = false;
				for (int n = segment.from(); n <= segment.to(); n++) {
					SeatRow seat = new SeatRow(segment.rowLabel(), n, segment.x() + (n - segment.from()) * SEAT_STEP,
							segment.y());
					if (existingSeats.contains(segment.rowLabel() + "|" + n)) {
						anyExisting = true;
						if (context.onDuplicate() == DuplicateMode.UPDATE) {
							toUpdate.add(seat);
						}
					}
					else {
						anyNew = true;
						toInsert.add(seat);
					}
				}
				if (anyNew) {
					created++;
				}
				else if (anyExisting && context.onDuplicate() == DuplicateMode.UPDATE) {
					updated++;
				}
				else {
					skipped++;
				}
			}
			if (!context.dryRun()) {
				maps.insertSeats(sectionId, toInsert);
				maps.updateSeatPositions(sectionId, toUpdate);
			}
		}
		return new int[] { created, updated, skipped };
	}

	private static ImportOutcome outcome(int[] counts, List<RowError> errors) {
		return new ImportOutcome(counts[0], counts[1], counts[2], errors);
	}

	/** Cột x / y tuỳ chọn: trống là null; sai định dạng ghi lỗi và trả {@code Integer.MIN_VALUE}. */
	private static Integer optionalInt(ImportRow row, String column, List<RowError> errors) {
		String value = row.get(column);
		if (value.isEmpty()) {
			return null;
		}
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e) {
			errors.add(new RowError(row.number(), column, "Toạ độ phải là số nguyên"));
			return Integer.MIN_VALUE;
		}
	}

	/** Trả 0 khi không phải số nguyên, để chỗ gọi báo lỗi chung. */
	private static int parseInt(String value) {
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}

}
