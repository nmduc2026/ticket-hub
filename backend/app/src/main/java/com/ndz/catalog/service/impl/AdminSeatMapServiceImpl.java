package com.ndz.catalog.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;

import com.ndz.catalog.client.SeatMapLock;
import com.ndz.catalog.dto.request.SeatMapRequest;
import com.ndz.catalog.dto.request.SeatMapRequest.ElementInput;
import com.ndz.catalog.dto.request.SeatMapRequest.SectionInput;
import com.ndz.catalog.dto.response.AdminSeatMapResponses.Summary;
import com.ndz.catalog.dto.response.CatalogViews.SeatMapRow;
import com.ndz.catalog.dto.response.CatalogViews.SeatMapView;
import com.ndz.catalog.dto.response.VenueResponses.SeatMapResponse;
import com.ndz.catalog.mapper.VenueMapper;
import com.ndz.catalog.repository.CatalogReadRepository;
import com.ndz.catalog.repository.SeatMapAdminRepository;
import com.ndz.catalog.repository.SeatMapAdminRepository.ElementRow;
import com.ndz.catalog.repository.SeatMapAdminRepository.SeatRow;
import com.ndz.catalog.repository.SeatMapAdminRepository.StoredSection;
import com.ndz.catalog.service.AdminSeatMapService;
import com.ndz.catalog.util.SeatGenerator;
import com.ndz.catalog.util.SeatGenerator.GeneratedSeat;
import com.ndz.catalog.util.SeatGenerator.GridParams;
import com.ndz.shared.audit.AuditAction;
import com.ndz.shared.audit.AuditLogService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.Uuid7;

/**
 * Lưu sơ đồ giữ khoá {@code lock:seatmap:{id}} suốt giao dịch (khoá nhả sau khi commit), nên hai Admin lưu cùng lúc
 * thì một bên nhận {@code SEAT_MAP_BEING_EDITED}. Vì vậy các hàm ghi không dùng {@code @Transactional} mà mở
 * {@link TransactionTemplate} bên trong khoá.
 */
@Service
class AdminSeatMapServiceImpl implements AdminSeatMapService {

	/** Giới hạn tổng số ghế sinh ra trong một sơ đồ, để một lần lưu không tạo hàng triệu dòng. */
	private static final int MAX_GENERATED_SEATS = 50_000;
	private static final int MAX_ROWS = 200;
	private static final int MAX_SEATS_PER_ROW = 200;

	private final SeatMapAdminRepository maps;
	private final CatalogReadRepository catalog;
	private final VenueMapper mapper;
	private final SeatMapLock lock;
	private final TransactionTemplate tx;
	private final AuditLogService audit;

	AdminSeatMapServiceImpl(SeatMapAdminRepository maps, CatalogReadRepository catalog, VenueMapper mapper,
			SeatMapLock lock, TransactionTemplate tx, AuditLogService audit) {
		this.maps = maps;
		this.catalog = catalog;
		this.mapper = mapper;
		this.lock = lock;
		this.tx = tx;
		this.audit = audit;
	}

	@Override
	public List<Summary> list(UUID venueId) {
		return maps.listAll(venueId).stream()
				.map(item -> new Summary(item.map().id(), item.map().venueId(), item.map().name(), item.map().version(),
						item.map().width(), item.map().height(), item.map().locked(), item.sectionCount(), item.seatCount()))
				.toList();
	}

	@Override
	public SeatMapResponse get(UUID id) {
		return tx.execute(status -> view(find(id)));
	}

	@Override
	public SeatMapResponse create(UUID adminId, SeatMapRequest request) {
		if (request.venueId() == null || !maps.venueExists(request.venueId())) {
			throw new BusinessException(ErrorCode.VENUE_NOT_FOUND, "Không tìm thấy địa điểm");
		}
		UUID id = Uuid7.next();
		String name = request.name().trim();
		return withLock(id, () -> tx.execute(status -> {
			try {
				maps.insertMap(id, request.venueId(), name, 1, request.width(), request.height());
			}
			catch (DuplicateKeyException e) {
				throw new BusinessException(ErrorCode.SEAT_MAP_NAME_DUPLICATE, "Địa điểm đã có sơ đồ cùng tên");
			}
			save(id, request);
			audit.record(adminId, AuditAction.SEAT_MAP_CREATED, "seat_map", id, null,
					Map.of("venueId", request.venueId(), "name", name));
			return view(find(id));
		}));
	}

	@Override
	public SeatMapResponse update(UUID adminId, UUID id, SeatMapRequest request) {
		find(id);
		return withLock(id, () -> tx.execute(status -> {
			SeatMapRow before = find(id);
			if (before.locked()) {
				throw new BusinessException(ErrorCode.SEAT_MAP_LOCKED, "Sơ đồ đã có sự kiện publish dùng, hãy tạo bản sao để sửa");
			}
			try {
				save(id, request);
			}
			catch (DuplicateKeyException e) {
				throw new BusinessException(ErrorCode.SEAT_MAP_NAME_DUPLICATE, "Địa điểm đã có sơ đồ cùng tên");
			}
			audit.record(adminId, AuditAction.SEAT_MAP_UPDATED, "seat_map", id, Map.of("name", before.name()),
					Map.of("name", request.name().trim(), "sections", request.sections().size()));
			return view(find(id));
		}));
	}

	@Override
	public SeatMapResponse copy(UUID adminId, UUID id) {
		SeatMapRow source = find(id);
		return tx.execute(status -> {
			UUID copyId = Uuid7.next();
			maps.insertMap(copyId, source.venueId(), source.name(), maps.nextVersion(source.venueId(), source.name()),
					source.width(), source.height());
			for (StoredSection section : maps.sectionsByCode(id).values()) {
				UUID sectionId = Uuid7.next();
				maps.insertSection(sectionId, copyId, section.code(), section.name(), section.shapeJson());
				maps.insertSeats(sectionId, maps.seatsOf(section.id()));
			}
			maps.replaceElements(copyId, catalog.listElements(List.of(id)).stream()
					.map(element -> new ElementRow(element.type(), element.label(), element.shapeJson())).toList());
			audit.record(adminId, AuditAction.SEAT_MAP_COPIED, "seat_map", copyId, Map.of("sourceId", id),
					Map.of("name", source.name()));
			return view(find(copyId));
		});
	}

	@Override
	public void delete(UUID adminId, UUID id) {
		find(id);
		withLock(id, () -> tx.execute(status -> {
			SeatMapRow map = find(id);
			if (map.locked()) {
				throw new BusinessException(ErrorCode.SEAT_MAP_LOCKED, "Sơ đồ đã khoá, không xoá được");
			}
			if (maps.usedByEvent(id)) {
				throw new BusinessException(ErrorCode.SEAT_MAP_IN_USE, "Đã có sự kiện dùng sơ đồ này");
			}
			maps.deleteMap(id);
			audit.record(adminId, AuditAction.SEAT_MAP_DELETED, "seat_map", id, Map.of("name", map.name()), null);
			return null;
		}));
	}

	/** Ghi khu, ghế và phần tử; gọi trong giao dịch, đã giữ khoá. */
	private void save(UUID id, SeatMapRequest request) {
		List<ParsedSection> parsed = request.sections().stream().map(AdminSeatMapServiceImpl::parse).toList();
		Set<String> codes = new HashSet<>();
		int generated = 0;
		for (ParsedSection section : parsed) {
			if (!codes.add(section.input().code())) {
				throw invalid("Mã khu " + section.input().code() + " bị trùng");
			}
			if (section.grid() != null) {
				generated += section.grid().rows() * section.grid().seatsPerRow();
			}
		}
		if (generated > MAX_GENERATED_SEATS) {
			throw invalid("Sơ đồ có quá " + MAX_GENERATED_SEATS + " ghế");
		}

		Map<String, StoredSection> existing = maps.sectionsByCode(id);
		for (StoredSection old : existing.values()) {
			if (!codes.contains(old.code())) {
				if (maps.sectionInUse(old.id())) {
					throw new BusinessException(ErrorCode.SECTION_IN_USE, "Khu " + old.code() + " đã gán hạng vé, không xoá được");
				}
				maps.deleteSection(old.id());
			}
		}
		maps.updateMap(id, request.name().trim(), request.width(), request.height());
		for (ParsedSection section : parsed) {
			SectionInput input = section.input();
			StoredSection stored = existing.get(input.code());
			String shapeJson = input.shape().toString();
			if (section.grid() == null) { // IMPORTED: chỉ đổi tên / vị trí, ghế giữ nguyên
				if (stored == null) {
					throw invalid("Khu IMPORTED " + input.code() + " chỉ tạo được bằng import Excel");
				}
				maps.updateSection(stored.id(), input.name().trim(), shapeJson);
				continue;
			}
			UUID sectionId = stored == null ? Uuid7.next() : stored.id();
			if (stored == null) {
				maps.insertSection(sectionId, id, input.code(), input.name().trim(), shapeJson);
			}
			else {
				maps.updateSection(sectionId, input.name().trim(), shapeJson);
			}
			List<GeneratedSeat> seats = SeatGenerator.generate(section.grid());
			maps.replaceSeats(sectionId, seats);
		}
		maps.replaceElements(id, request.elements().stream().map(AdminSeatMapServiceImpl::toElement).toList());
	}

	private record ParsedSection(SectionInput input, GridParams grid) {
	}

	/** {@code GRID} đọc tham số để sinh ghế; {@code IMPORTED} không có tham số lưới ({@code grid} null). */
	private static ParsedSection parse(SectionInput input) {
		JsonNode shape = input.shape();
		String kind = shape.path("kind").asString("");
		if ("IMPORTED".equals(kind)) {
			return new ParsedSection(input, null);
		}
		if (!"GRID".equals(kind)) {
			throw invalid("Khu " + input.code() + " có kind không hợp lệ (GRID hoặc IMPORTED)");
		}
		int rows = intIn(shape, "rows", 1, MAX_ROWS, input.code());
		int seatsPerRow = intIn(shape, "seatsPerRow", 1, MAX_SEATS_PER_ROW, input.code());
		double rowGap = positive(shape, "rowGap", input.code());
		double seatGap = positive(shape, "seatGap", input.code());
		List<String> hidden = new ArrayList<>();
		shape.path("hiddenSeats").forEach(seat -> hidden.add(seat.asString("")));
		String numbering = shape.path("numbering").asString("LTR");
		if (!"LTR".equals(numbering) && !"RTL".equals(numbering)) {
			throw invalid("Khu " + input.code() + ": numbering phải là LTR hoặc RTL");
		}
		return new ParsedSection(input, new GridParams(number(shape, "x", input.code()), number(shape, "y", input.code()),
				shape.path("rotation").asDouble(0), rows, seatsPerRow, rowGap, seatGap, shape.path("curve").asDouble(0),
				"RTL".equals(numbering), hidden));
	}

	private static int intIn(JsonNode shape, String field, int min, int max, String code) {
		JsonNode value = shape.path(field);
		if (!value.isIntegralNumber() || value.asInt() < min || value.asInt() > max) {
			throw invalid("Khu " + code + ": " + field + " phải là số nguyên từ " + min + " đến " + max);
		}
		return value.asInt();
	}

	private static double positive(JsonNode shape, String field, String code) {
		double value = number(shape, field, code);
		if (value <= 0) {
			throw invalid("Khu " + code + ": " + field + " phải lớn hơn 0");
		}
		return value;
	}

	private static double number(JsonNode shape, String field, String code) {
		JsonNode value = shape.path(field);
		if (!value.isNumber()) {
			throw invalid("Khu " + code + ": thiếu " + field);
		}
		return value.asDouble();
	}

	private static ElementRow toElement(ElementInput input) {
		for (String field : List.of("x", "y", "w", "h")) {
			if (!input.shape().path(field).isNumber()) {
				throw invalid("Phần tử " + input.type() + " thiếu " + field);
			}
		}
		return new ElementRow(input.type(), input.label(), input.shape().toString());
	}

	private static BusinessException invalid(String message) {
		return new BusinessException(ErrorCode.SEAT_MAP_INVALID, message);
	}

	private SeatMapRow find(UUID id) {
		return maps.find(id).orElseThrow(() -> new BusinessException(ErrorCode.SEAT_MAP_NOT_FOUND, "Không tìm thấy sơ đồ ghế"));
	}

	private SeatMapResponse view(SeatMapRow map) {
		List<UUID> ids = List.of(map.id());
		return mapper.toSeatMap(new SeatMapView(map, catalog.listSections(ids), catalog.listElements(ids)));
	}

	private <T> T withLock(UUID id, Supplier<T> action) {
		String token = lock.tryAcquire(id);
		if (token == null) {
			throw new BusinessException(ErrorCode.SEAT_MAP_BEING_EDITED, "Admin khác đang lưu sơ đồ này, thử lại sau");
		}
		try {
			return action.get();
		}
		finally {
			lock.release(id, token);
		}
	}

}
