package com.ndz.catalog.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.api.CatalogApi;
import com.ndz.catalog.repository.CatalogReadRepository;
import com.ndz.catalog.repository.EventRepository;
import com.ndz.catalog.repository.OrganizerRepository;

/** Cài đặt {@link CatalogApi} trong cùng process (giai đoạn modular monolith). */
@Service
class CatalogApiImpl implements CatalogApi {

	private final CatalogReadRepository catalog;
	private final EventRepository events;
	private final OrganizerRepository organizers;

	CatalogApiImpl(CatalogReadRepository catalog, EventRepository events, OrganizerRepository organizers) {
		this.catalog = catalog;
		this.events = events;
		this.organizers = organizers;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<OrganizerContact> organizerContact(UUID organizerId) {
		return organizers.findById(organizerId)
				.map(organizer -> new OrganizerContact(organizer.getStatus().name(), organizer.getContactEmail()));
	}

	@Override
	@Transactional(readOnly = true)
	public SeatMapLayout seatMapLayout(UUID seatMapId) {
		return catalog.seatMapLayout(seatMapId);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isEventOrganizer(UUID eventId, UUID userId) {
		return events.findByIdAndOrganizerId(eventId, userId).isPresent();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<EventSchedule> eventSchedule(UUID eventId) {
		return events.findById(eventId).map(event -> new EventSchedule(event.getStartsAt(), event.getEndsAt()));
	}

	@Override
	@Transactional(readOnly = true)
	public Map<UUID, GuestInfo> guestInfo(Collection<UUID> eventIds) {
		return events.findAllById(eventIds).stream().collect(Collectors.toMap(event -> event.getId(),
				event -> new GuestInfo(event.getDoorsOpenAt(), event.getAgeRestriction(), event.getRules())));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<String> provinceName(UUID provinceId) {
		return catalog.activeUnits(1, null, null).stream().filter(unit -> unit.id().equals(provinceId))
				.map(unit -> unit.name()).findFirst();
	}

	@Override
	@Transactional(readOnly = true)
	public List<OrganizerEvent> eventsOf(UUID organizerId, int limit) {
		return events.findByOrganizerId(organizerId, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "startsAt")))
				.stream()
				.map(event -> new OrganizerEvent(event.getId(), event.getTitle(), event.getStatus().name(),
						event.getStartsAt()))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<SellableSeat> sellableSeats(UUID eventId) {
		return catalog.sellableSeats(eventId);
	}

}
