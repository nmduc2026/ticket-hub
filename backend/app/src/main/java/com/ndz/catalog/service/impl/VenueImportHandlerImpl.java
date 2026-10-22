package com.ndz.catalog.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.catalog.dto.response.ImportResponses.ImportContext;
import com.ndz.catalog.dto.response.ImportResponses.ImportOutcome;
import com.ndz.catalog.dto.response.ImportResponses.RowError;
import com.ndz.catalog.entity.AdministrativeUnit;
import com.ndz.catalog.entity.Venue;
import com.ndz.catalog.entity.enums.DuplicateMode;
import com.ndz.catalog.entity.enums.ImportType;
import com.ndz.catalog.service.ImportHandler;
import com.ndz.catalog.repository.AdministrativeUnitRepository;
import com.ndz.catalog.repository.VenueRepository;
import com.ndz.catalog.util.ExcelFiles.GuideLine;
import com.ndz.catalog.util.ExcelFiles.ImportRow;
import com.ndz.shared.util.Uuid7;

/** Import địa điểm, khớp theo {@code ma} khi có (docs/design/README.md §7); dòng không có mã luôn là địa điểm mới. */
@Component
class VenueImportHandlerImpl implements ImportHandler {

	private static final List<String> COLUMNS = List.of("ma", "ten", "dia_chi", "ma_xa", "suc_chua");

	private final VenueRepository venues;
	private final AdministrativeUnitRepository units;
	private final TransactionTemplate tx;

	VenueImportHandlerImpl(VenueRepository venues, AdministrativeUnitRepository units, TransactionTemplate tx) {
		this.venues = venues;
		this.units = units;
		this.tx = tx;
	}

	@Override
	public ImportType type() {
		return ImportType.VENUES;
	}

	@Override
	public List<String> columns() {
		return COLUMNS;
	}

	@Override
	public Set<String> textColumns() {
		return Set.of("ma", "ma_xa");
	}

	@Override
	public List<List<String>> examples() {
		return List.of(List.of("NHAT-HOA-BINH", "Nhà hát Hòa Bình", "240 Ba Tháng Hai", "00001", "2000"),
				List.of("SVD-MY-DINH", "Sân vận động Mỹ Đình", "Đường Lê Đức Thọ", "00001", "40000"));
	}

	@Override
	public List<GuideLine> guide() {
		return List.of(
				new GuideLine("ma", false,
						"Mã để khớp khi import lại, không trùng trong file; để trống thì luôn tạo địa điểm mới"),
				new GuideLine("ten", true, "Tên địa điểm"), new GuideLine("dia_chi", true, "Số nhà, đường"),
				new GuideLine("ma_xa", true, "Mã Phường / Xã / Đặc khu đã có trong danh mục đơn vị hành chính"),
				new GuideLine("suc_chua", true, "Số nguyên lớn hơn 0"));
	}

	@Override
	public ImportOutcome run(List<ImportRow> rows, ImportContext context) {
		List<RowError> errors = new ArrayList<>();
		Map<String, Integer> seenCodes = new HashMap<>();
		Map<String, Optional<AdministrativeUnit>> wards = new HashMap<>();
		int[] counts = tx.execute(status -> {
			int created = 0;
			int updated = 0;
			int skipped = 0;
			for (ImportRow row : rows) {
				boolean ok = true;
				String code = row.get("ma");
				if (!code.isEmpty() && seenCodes.putIfAbsent(code, row.number()) != null) {
					errors.add(new RowError(row.number(), "ma", "Trùng mã với dòng " + seenCodes.get(code)));
					ok = false;
				}
				if (row.get("ten").isEmpty()) {
					errors.add(new RowError(row.number(), "ten", "Thiếu tên"));
					ok = false;
				}
				if (row.get("dia_chi").isEmpty()) {
					errors.add(new RowError(row.number(), "dia_chi", "Thiếu địa chỉ"));
					ok = false;
				}
				int capacity = parsePositiveInt(row.get("suc_chua"));
				if (row.get("suc_chua").isEmpty()) {
					errors.add(new RowError(row.number(), "suc_chua", "Thiếu sức chứa"));
					ok = false;
				}
				else if (capacity <= 0) {
					errors.add(new RowError(row.number(), "suc_chua", "Sức chứa phải là số nguyên lớn hơn 0"));
					ok = false;
				}
				UUID wardId = null;
				if (row.get("ma_xa").isEmpty()) {
					errors.add(new RowError(row.number(), "ma_xa", "Thiếu mã xã / phường"));
					ok = false;
				}
				else {
					Optional<AdministrativeUnit> ward = wards.computeIfAbsent(row.get("ma_xa"), units::findByCode);
					if (ward.isEmpty()) {
						errors.add(new RowError(row.number(), "ma_xa", "Mã xã / phường không tồn tại"));
						ok = false;
					}
					else if (ward.get().getLevel() != 2) {
						errors.add(new RowError(row.number(), "ma_xa", "Mã phải thuộc cấp Xã / Phường / Đặc khu"));
						ok = false;
					}
					else {
						wardId = ward.get().getId();
					}
				}
				if (!ok) {
					continue;
				}
				Venue existing = code.isEmpty() ? null : venues.findByCode(code).orElse(null);
				if (existing == null) {
					created++;
					if (!context.dryRun()) {
						venues.save(new Venue(Uuid7.next(), code.isEmpty() ? null : code, row.get("ten"),
								row.get("dia_chi"), wardId, capacity));
					}
				}
				else if (context.onDuplicate() == DuplicateMode.SKIP) {
					skipped++;
				}
				else {
					updated++;
					if (!context.dryRun()) {
						existing.update(row.get("ten"), row.get("dia_chi"), wardId, capacity);
					}
				}
			}
			return new int[] { created, updated, skipped };
		});
		return new ImportOutcome(counts[0], counts[1], counts[2], errors);
	}

	/** Trả 0 khi không phải số nguyên dương nhỏ hơn 2 tỷ, để chỗ gọi báo lỗi chung. */
	private static int parsePositiveInt(String value) {
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}

}
