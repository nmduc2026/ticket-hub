package com.ndz.ticket.service.impl;

import java.text.ParseException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.jwk.ECKey;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.api.CatalogApi;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.Uuid7;
import com.ndz.ticket.dto.response.CheckInResponses.CheckInResponse;
import com.ndz.ticket.dto.response.CheckInResponses.KeySetResponse;
import com.ndz.ticket.dto.response.CheckInResponses.SigningKey;
import com.ndz.ticket.entity.Ticket;
import com.ndz.ticket.entity.enums.TicketStatus;
import com.ndz.ticket.repository.CheckInRepository;
import com.ndz.ticket.repository.SigningKeyRepository;
import com.ndz.ticket.repository.TicketRepository;
import com.ndz.ticket.service.CheckInService;
import com.ndz.ticket.util.QrTokenVerifier;

@Service
public class CheckInServiceImpl implements CheckInService {

	private static final Logger log = LoggerFactory.getLogger(CheckInServiceImpl.class);

	private final QrTokenVerifier verifier;
	private final TicketRepository tickets;
	private final CheckInRepository checkIns;
	private final SigningKeyRepository keys;
	private final CatalogApi catalog;
	private final MeterRegistry metrics;
	private final Clock clock;

	CheckInServiceImpl(QrTokenVerifier verifier, TicketRepository tickets, CheckInRepository checkIns,
			SigningKeyRepository keys, CatalogApi catalog, MeterRegistry metrics, Clock clock) {
		this.metrics = metrics;
		this.verifier = verifier;
		this.tickets = tickets;
		this.checkIns = checkIns;
		this.keys = keys;
		this.catalog = catalog;
		this.clock = clock;
	}

	@Override
	@Transactional
	public CheckInResponse checkIn(UUID organizerUserId, String qrToken, String gate) {
		try {
			CheckInResponse response = doCheckIn(organizerUserId, qrToken, gate);
			metrics.counter("tickethub.checkin", "result", "OK").increment();
			return response;
		}
		catch (BusinessException e) {
			metrics.counter("tickethub.checkin", "result", e.code().name()).increment();
			throw e;
		}
	}

	private CheckInResponse doCheckIn(UUID organizerUserId, String qrToken, String gate) {
		QrTokenVerifier.Claims claims = verifier.verify(qrToken)
				.orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND, "Vé không hợp lệ"));
		Ticket ticket = tickets.findById(claims.ticketId()).filter(found -> found.getEventId().equals(claims.eventId()))
				.orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND, "Vé không hợp lệ"));
		// Quyền kiểm trước khi nói gì về trạng thái vé: organizer khác không được dò vé của sự kiện không phải của mình.
		if (!catalog.isEventOrganizer(ticket.getEventId(), organizerUserId)) {
			throw new BusinessException(ErrorCode.NOT_EVENT_OWNER, "Bạn không phải ban tổ chức của sự kiện này");
		}
		// BR-09: một vé chỉ check-in một lần; nguyên tử để hai thiết bị quét cùng lúc chỉ một nơi thành công.
		if (tickets.markUsed(ticket.getId()) == 0) {
			throw notUsable(ticket.getId());
		}
		checkIns.insert(Uuid7.next(clock), ticket.getId(), gate, organizerUserId, clock.instant());
		log.info("Check-in vé {} sự kiện {} cổng {}", ticket.getId(), ticket.getEventId(), gate);
		return new CheckInResponse(ticket.getId(), ticket.getSectionCode() + "-" + ticket.getRowLabel() + "-"
				+ ticket.getSeatNumber(), TicketStatus.USED);
	}

	@Override
	@Transactional(readOnly = true)
	public KeySetResponse signingKeys() {
		List<SigningKey> signingKeys = keys.findAllPublicKeys().stream().map(CheckInServiceImpl::toPublicKey).toList();
		return new KeySetResponse(signingKeys);
	}

	/** Đọc lại trạng thái hiện tại để báo đúng lý do khi {@code markUsed} không đổi được dòng nào. */
	private BusinessException notUsable(UUID ticketId) {
		TicketStatus status = tickets.findById(ticketId).map(Ticket::getStatus).orElse(null);
		if (status == TicketStatus.USED) {
			return new BusinessException(ErrorCode.TICKET_ALREADY_USED, "Vé này đã được dùng để vào cổng");
		}
		if (status == TicketStatus.REVOKED) {
			return new BusinessException(ErrorCode.TICKET_REVOKED, "Vé đã bị thu hồi do hoàn tiền");
		}
		return new BusinessException(ErrorCode.TICKET_NOT_FOUND, "Vé không hợp lệ");
	}

	private static SigningKey toPublicKey(String jwk) {
		try {
			ECKey key = ECKey.parse(jwk);
			return new SigningKey(key.getKeyID(), key.getKeyType().getValue(), key.getCurve().getName(),
					key.getX().toString(), key.getY().toString());
		}
		catch (ParseException e) {
			throw new IllegalStateException("Khoá công khai trong signing_keys không đọc được", e);
		}
	}

}
