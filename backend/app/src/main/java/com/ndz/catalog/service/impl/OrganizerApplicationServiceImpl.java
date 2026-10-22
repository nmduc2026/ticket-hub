package com.ndz.catalog.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.dto.request.OrganizerApplyRequest;
import com.ndz.catalog.dto.response.OrganizerResponses.Application;
import com.ndz.catalog.dto.response.OrganizerResponses.ApplyResult;
import com.ndz.catalog.entity.Organizer;
import com.ndz.catalog.entity.enums.OrganizerType;
import com.ndz.catalog.event.OrganizerApplied;
import com.ndz.catalog.mapper.OrganizerMapper;
import com.ndz.catalog.repository.OrganizerRepository;
import com.ndz.catalog.service.OrganizerApplicationService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.Uuid7;
import com.ndz.shared.util.VietnamesePhone;

/**
 * Đăng ký làm organizer (overview.md §7.6). Khoá dòng organizer của chính người nộp nên hai lần nộp đồng thời chỉ một
 * lần tạo hồ sơ; lần còn lại thấy hồ sơ {@code PENDING}.
 */
@Service
public class OrganizerApplicationServiceImpl implements OrganizerApplicationService {

	private static final Logger log = LoggerFactory.getLogger(OrganizerApplicationServiceImpl.class);

	private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-]");
	private static final Pattern TAX_CODE = Pattern.compile("\\d{10}(\\d{3})?");
	private static final Pattern CITIZEN_ID = Pattern.compile("\\d{12}");
	private static final Pattern URL = Pattern.compile("https?://\\S+");

	private final OrganizerRepository organizers;
	private final OrganizerMapper mapper;
	private final ApplicationEventPublisher publisher;
	private final Clock clock;

	OrganizerApplicationServiceImpl(OrganizerRepository organizers, OrganizerMapper mapper,
			ApplicationEventPublisher publisher, Clock clock) {
		this.organizers = organizers;
		this.mapper = mapper;
		this.publisher = publisher;
		this.clock = clock;
	}

	@Override
	@Transactional
	public ApplyResult apply(UUID userId, OrganizerApplyRequest request) {
		Organizer.Application application = normalize(request);
		Instant now = clock.instant();
		Organizer organizer = organizers.lockById(userId).orElse(null);
		if (organizer == null) {
			organizer = Organizer.apply(userId, application, now);
			try {
				organizers.saveAndFlush(organizer);
			}
			catch (DataIntegrityViolationException e) {
				// Hai lần nộp đầu tiên cùng lúc: bên thua thấy hồ sơ của bên thắng.
				throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PENDING, "Hồ sơ của bạn đang chờ duyệt");
			}
		}
		else {
			organizer.resubmit(application, now);
		}
		publisher.publishEvent(new OrganizerApplied(Uuid7.next(clock), now, userId, application.displayName(),
				application.contactEmail()));
		log.info("Organizer {} nộp hồ sơ ({})", userId, application.type());
		return new ApplyResult(organizer.getStatus());
	}

	@Override
	@Transactional(readOnly = true)
	public Application myApplication(UUID userId) {
		return organizers.findById(userId).filter(organizer -> organizer.getSubmittedAt() != null)
				.map(mapper::toApplication).orElseThrow(
						() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND, "Bạn chưa nộp hồ sơ"));
	}

	@Override
	@Transactional
	public Application updateProfile(UUID userId, OrganizerApplyRequest request) {
		Organizer.Application application = normalize(request);
		Organizer organizer = organizers.lockById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORGANIZER_NOT_FOUND, "Không tìm thấy hồ sơ"));
		organizer.updateProfile(application, clock.instant());
		log.info("Organizer {} sửa hồ sơ", userId);
		return mapper.toApplication(organizer);
	}

	/** Chuẩn hoá khoảng trắng / dấu phân cách và kiểm mã số theo loại organizer. */
	private static Organizer.Application normalize(OrganizerApplyRequest request) {
		String legalId = SEPARATORS.matcher(request.legalId()).replaceAll("");
		Pattern expected = request.type() == OrganizerType.BUSINESS ? TAX_CODE : CITIZEN_ID;
		if (!expected.matcher(legalId).matches()) {
			throw new BusinessException(ErrorCode.LEGAL_ID_INVALID,
					request.type() == OrganizerType.BUSINESS ? "Mã số thuế gồm 10 hoặc 13 chữ số"
							: "Số CCCD gồm 12 chữ số");
		}
		String phone = SEPARATORS.matcher(request.contactPhone()).replaceAll("");
		if (!VietnamesePhone.isValid(phone)) {
			throw new BusinessException(ErrorCode.PHONE_INVALID, "Số điện thoại không hợp lệ");
		}
		String website = blankToNull(request.website());
		if (website != null && !URL.matcher(website).matches()) {
			throw new BusinessException(ErrorCode.WEBSITE_INVALID, "Website phải bắt đầu bằng http:// hoặc https://");
		}
		return new Organizer.Application(request.type(), request.displayName().trim(), request.legalName().trim(),
				legalId, request.contactEmail().trim(), phone, website, request.introduction().trim(),
				blankToNull(request.eventScale()));
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

}
