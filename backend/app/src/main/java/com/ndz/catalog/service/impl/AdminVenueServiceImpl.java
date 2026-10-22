package com.ndz.catalog.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.dto.request.VenueRequest;
import com.ndz.catalog.dto.response.AdminVenueResponse;
import com.ndz.catalog.dto.response.CatalogViews.VenueRow;
import com.ndz.catalog.dto.response.SuspensionResponses.BulkItem;
import com.ndz.catalog.dto.response.SuspensionResponses.BulkResult;
import com.ndz.catalog.entity.AdministrativeUnit;
import com.ndz.catalog.entity.Venue;
import com.ndz.catalog.entity.enums.AdministrativeUnitType;
import com.ndz.catalog.repository.AdministrativeUnitRepository;
import com.ndz.catalog.repository.CatalogReadRepository;
import com.ndz.catalog.repository.EventRepository;
import com.ndz.catalog.repository.VenueRepository;
import com.ndz.catalog.service.AdminVenueService;
import com.ndz.shared.audit.AuditAction;
import com.ndz.shared.audit.AuditLogService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.Uuid7;

@Service
class AdminVenueServiceImpl implements AdminVenueService {

	private final VenueRepository venues;
	private final AdministrativeUnitRepository units;
	private final EventRepository events;
	private final CatalogReadRepository catalog;
	private final AuditLogService audit;

	AdminVenueServiceImpl(VenueRepository venues, AdministrativeUnitRepository units, EventRepository events,
			CatalogReadRepository catalog, AuditLogService audit) {
		this.venues = venues;
		this.units = units;
		this.events = events;
		this.catalog = catalog;
		this.audit = audit;
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdminVenueResponse> list() {
		return catalog.listVenues().stream().map(AdminVenueServiceImpl::toResponse).toList();
	}

	@Override
	@Transactional
	public AdminVenueResponse create(UUID adminId, VenueRequest request) {
		requireWard(request.wardId());
		Venue venue = new Venue(Uuid7.next(), request.name().trim(), request.address().trim(), request.wardId(),
				request.capacity());
		venues.save(venue);
		audit.record(adminId, AuditAction.VENUE_CREATED, "venue", venue.getId(), null,
				Map.of("name", venue.getName(), "wardId", venue.getWardId()));
		return toResponse(venue);
	}

	@Override
	@Transactional
	public AdminVenueResponse update(UUID adminId, UUID id, VenueRequest request) {
		requireWard(request.wardId());
		Venue venue = venues.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.VENUE_NOT_FOUND, "Không tìm thấy địa điểm"));
		Map<String, Object> before = Map.of("name", venue.getName(), "address", venue.getAddress(), "wardId",
				venue.getWardId(), "capacity", venue.getCapacity());
		venue.update(request.name().trim(), request.address().trim(), request.wardId(), request.capacity());
		Map<String, Object> after = Map.of("name", venue.getName(), "address", venue.getAddress(), "wardId",
				venue.getWardId(), "capacity", venue.getCapacity());
		audit.record(adminId, AuditAction.VENUE_UPDATED, "venue", id, before, after);
		return toResponse(venue);
	}

	@Override
	@Transactional
	public BulkResult bulkDelete(UUID adminId, List<UUID> ids) {
		List<BulkItem> results = ids.stream().map(id -> deleteOne(adminId, id)).toList();
		return new BulkResult(results);
	}

	private BulkItem deleteOne(UUID adminId, UUID id) {
		Venue venue = venues.findById(id).orElse(null);
		if (venue == null) {
			return new BulkItem(id, false, ErrorCode.VENUE_NOT_FOUND.name());
		}
		if (events.existsByVenueId(id)) {
			return new BulkItem(id, false, ErrorCode.VENUE_IN_USE.name());
		}
		venues.delete(venue);
		audit.record(adminId, AuditAction.VENUE_DELETED, "venue", id, Map.of("name", venue.getName()), null);
		return new BulkItem(id, true, null);
	}

	/** {@code wardId} phải trỏ đơn vị cấp 2 (FK không kiểm được cấp). */
	private void requireWard(UUID wardId) {
		AdministrativeUnit ward = units.findById(wardId)
				.orElseThrow(() -> new BusinessException(ErrorCode.WARD_LEVEL_INVALID, "Không tìm thấy xã / phường"));
		if (ward.getLevel() != 2 || ward.getType() == AdministrativeUnitType.TINH
				|| ward.getType() == AdministrativeUnitType.THANH_PHO) {
			throw new BusinessException(ErrorCode.WARD_LEVEL_INVALID, "wardId phải là đơn vị cấp xã / phường");
		}
	}

	private static AdminVenueResponse toResponse(VenueRow row) {
		return new AdminVenueResponse(row.id(), row.name(), row.address(), row.wardId(), row.wardName(),
				row.cityName(), row.capacity());
	}

	/** Venue vừa tạo / sửa chưa có tên xã / thành phố join sẵn: đọc lại qua CatalogReadRepository nếu cần hiển thị. */
	private AdminVenueResponse toResponse(Venue venue) {
		return catalog.listVenues().stream().filter(row -> row.id().equals(venue.getId())).map(AdminVenueServiceImpl::toResponse)
				.findFirst()
				.orElseGet(() -> new AdminVenueResponse(venue.getId(), venue.getName(), venue.getAddress(),
						venue.getWardId(), "", "", venue.getCapacity()));
	}

}
