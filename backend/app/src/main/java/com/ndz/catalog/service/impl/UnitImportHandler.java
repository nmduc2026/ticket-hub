package com.ndz.catalog.service.impl;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.ndz.catalog.dto.response.ImportResponses.RowError;
import com.ndz.catalog.entity.AdministrativeUnit;
import com.ndz.catalog.entity.enums.AdministrativeUnitType;
import com.ndz.catalog.entity.enums.DuplicateMode;
import com.ndz.catalog.entity.enums.ImportType;
import com.ndz.catalog.repository.AdministrativeUnitRepository;
import com.ndz.catalog.util.AdministrativeUnitCache;
import com.ndz.catalog.util.ExcelFiles.GuideLine;
import com.ndz.catalog.util.ExcelFiles.ImportRow;
import com.ndz.shared.util.Uuid7;

/** Import đơn vị hành chính, khớp theo {@code ma} (docs/design/README.md §7). */
@Component
class UnitImportHandler implements ImportHandler {

	private static final List<String> COLUMNS = List.of("ma", "ten", "loai", "ma_cha", "hieu_luc_tu");

	/** Một dòng đã qua kiểm cơ bản, chờ phân giải đơn vị cha và ghi. */
	private record Planned(ImportRow row, String code, String name, AdministrativeUnitType type, short level,
			String parentCode, LocalDate effectiveFrom, UUID existingId) {
	}

	private final AdministrativeUnitRepository units;
	private final AdministrativeUnitCache cache;
	private final TransactionTemplate tx;
	private final Clock clock;

	UnitImportHandler(AdministrativeUnitRepository units, AdministrativeUnitCache cache, TransactionTemplate tx,
			Clock clock) {
		this.units = units;
		this.cache = cache;
		this.tx = tx;
		this.clock = clock;
	}

	@Override
	public ImportType type() {
		return ImportType.ADMINISTRATIVE_UNITS;
	}

	@Override
	public List<String> columns() {
		return COLUMNS;
	}

	@Override
	public Set<String> textColumns() {
		return Set.of("ma", "ma_cha");
	}

	@Override
	public List<List<String>> examples() {
		return List.of(List.of("01", "Thành phố Hà Nội", "Thành phố", "", "2025-07-01"),
				List.of("00001", "Phường Ba Đình", "Phường", "01", "2025-07-01"));
	}

	@Override
	public List<GuideLine> guide() {
		return List.of(new GuideLine("ma", true, "Mã đơn vị trong danh mục chính thức, không trùng trong file"),
				new GuideLine("ten", true, "Tên đơn vị"),
				new GuideLine("loai", true, "Tỉnh, Thành phố, Phường, Xã hoặc Đặc khu"),
				new GuideLine("ma_cha", false,
						"Bắt buộc với Phường / Xã / Đặc khu: mã của Tỉnh / Thành phố; để trống với Tỉnh / Thành phố"),
				new GuideLine("hieu_luc_tu", false, "Ngày có hiệu lực, dạng yyyy-MM-dd; để trống thì lấy ngày import"));
	}

	@Override
	public Outcome run(List<ImportRow> rows, Context context) {
		List<RowError> errors = new ArrayList<>();
		List<Planned> planned = validate(rows, errors);
		int[] counts = tx.execute(status -> apply(planned, context, errors));
		if (!context.dryRun()) {
			cache.evictAfterCommit();
		}
		return new Outcome(counts[0], counts[1], counts[2], errors);
	}

	private List<Planned> validate(List<ImportRow> rows, List<RowError> errors) {
		Map<String, Integer> seen = new HashMap<>();
		List<Planned> planned = new ArrayList<>();
		for (ImportRow row : rows) {
			String code = row.get("ma");
			String name = row.get("ten");
			boolean ok = true;
			if (code.isEmpty()) {
				errors.add(new RowError(row.number(), "ma", "Thiếu mã"));
				ok = false;
			}
			else if (seen.putIfAbsent(code, row.number()) != null) {
				errors.add(new RowError(row.number(), "ma", "Trùng mã với dòng " + seen.get(code)));
				ok = false;
			}
			if (name.isEmpty()) {
				errors.add(new RowError(row.number(), "ten", "Thiếu tên"));
				ok = false;
			}
			Optional<AdministrativeUnitType> type = parseType(row.get("loai"));
			if (row.get("loai").isEmpty()) {
				errors.add(new RowError(row.number(), "loai", "Thiếu loại"));
				ok = false;
			}
			else if (type.isEmpty()) {
				errors.add(new RowError(row.number(), "loai", "Loại không hợp lệ (Tỉnh, Thành phố, Phường, Xã, Đặc khu)"));
				ok = false;
			}
			LocalDate effectiveFrom = LocalDate.now(clock);
			if (!row.get("hieu_luc_tu").isEmpty()) {
				try {
					effectiveFrom = LocalDate.parse(row.get("hieu_luc_tu"));
				}
				catch (DateTimeParseException e) {
					errors.add(new RowError(row.number(), "hieu_luc_tu", "Ngày phải có dạng yyyy-MM-dd"));
					ok = false;
				}
			}
			String parentCode = row.get("ma_cha");
			if (ok) {
				short level = levelOf(type.get());
				if (level == 1 && !parentCode.isEmpty()) {
					errors.add(new RowError(row.number(), "ma_cha", "Tỉnh / Thành phố không có đơn vị cha"));
					ok = false;
				}
				else if (level == 2 && parentCode.isEmpty()) {
					errors.add(new RowError(row.number(), "ma_cha", "Cấp xã phải có mã cha"));
					ok = false;
				}
				if (ok) {
					UUID existing = units.findByCode(code).map(AdministrativeUnit::getId).orElse(null);
					planned.add(new Planned(row, code, name, type.get(), level, parentCode, effectiveFrom, existing));
				}
			}
		}
		return planned;
	}

	/** Ghi (hoặc chỉ đếm khi dry-run) theo thứ tự cấp 1 trước cấp 2; trả {tạo mới, cập nhật, bỏ qua}. */
	private int[] apply(List<Planned> planned, Context context, List<RowError> errors) {
		Map<String, UUID> idByCode = new HashMap<>();
		Map<String, Short> levelByCode = new HashMap<>();
		for (Planned item : planned) {
			idByCode.put(item.code(), item.existingId() != null ? item.existingId() : Uuid7.next());
			levelByCode.put(item.code(), item.level());
		}
		int created = 0;
		int updated = 0;
		int skipped = 0;
		for (short level = 1; level <= 2; level++) {
			for (Planned item : planned) {
				if (item.level() != level) {
					continue;
				}
				UUID parentId = null;
				if (level == 2) {
					parentId = resolveParent(item, idByCode, levelByCode, errors);
					if (parentId == null) {
						continue;
					}
				}
				if (item.existingId() == null) {
					created++;
					if (!context.dryRun()) {
						units.save(new AdministrativeUnit(idByCode.get(item.code()), item.code(), item.name(),
								item.type(), level, parentId, item.effectiveFrom()));
					}
				}
				else if (context.onDuplicate() == DuplicateMode.SKIP) {
					skipped++;
				}
				else {
					AdministrativeUnit existing = units.findById(item.existingId()).orElseThrow();
					if (existing.getType() != item.type() || (level == 2 && !parentId.equals(existing.getParentId()))) {
						errors.add(new RowError(item.row().number(), "loai",
								"Không đổi được loại hoặc đơn vị cha của đơn vị đã có"));
						continue;
					}
					updated++;
					if (!context.dryRun()) {
						existing.update(item.name(), item.code(), item.effectiveFrom());
					}
				}
			}
		}
		return new int[] { created, updated, skipped };
	}

	/** Đơn vị cha là dòng cùng file (đã hợp lệ) hoặc đã có trong DB; phải là cấp 1. */
	private UUID resolveParent(Planned item, Map<String, UUID> idByCode, Map<String, Short> levelByCode,
			List<RowError> errors) {
		if (levelByCode.containsKey(item.parentCode())) {
			if (levelByCode.get(item.parentCode()) != 1) {
				errors.add(new RowError(item.row().number(), "ma_cha", "Mã cha phải là Tỉnh / Thành phố"));
				return null;
			}
			return idByCode.get(item.parentCode());
		}
		Optional<AdministrativeUnit> parent = units.findByCode(item.parentCode());
		if (parent.isEmpty()) {
			errors.add(new RowError(item.row().number(), "ma_cha", "Mã cha không tồn tại"));
			return null;
		}
		if (parent.get().getLevel() != 1) {
			errors.add(new RowError(item.row().number(), "ma_cha", "Mã cha phải là Tỉnh / Thành phố"));
			return null;
		}
		return parent.get().getId();
	}

	private static short levelOf(AdministrativeUnitType type) {
		return (type == AdministrativeUnitType.TINH || type == AdministrativeUnitType.THANH_PHO) ? (short) 1
				: (short) 2;
	}

	/** "Thành phố" và "thanh pho" đều nhận: bỏ dấu để người dùng gõ không dấu vẫn đúng. */
	private static Optional<AdministrativeUnitType> parseType(String value) {
		String key = Normalizer.normalize(value.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.replace('đ', 'd');
		return switch (key) {
			case "tinh" -> Optional.of(AdministrativeUnitType.TINH);
			case "thanh pho" -> Optional.of(AdministrativeUnitType.THANH_PHO);
			case "phuong" -> Optional.of(AdministrativeUnitType.PHUONG);
			case "xa" -> Optional.of(AdministrativeUnitType.XA);
			case "dac khu" -> Optional.of(AdministrativeUnitType.DAC_KHU);
			default -> Optional.empty();
		};
	}

}
