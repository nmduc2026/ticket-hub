package com.ndz.catalog.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.catalog.config.CatalogProperties;
import com.ndz.catalog.dto.request.PlatformFeeRequest;
import com.ndz.catalog.dto.response.PlatformFeeResponse;
import com.ndz.catalog.entity.PlatformFeePolicy;
import com.ndz.catalog.repository.PlatformFeePolicyRepository;
import com.ndz.catalog.service.PlatformFeeService;
import com.ndz.shared.audit.AuditAction;
import com.ndz.shared.audit.AuditLogService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.Uuid7;

@Service
public class PlatformFeeServiceImpl implements PlatformFeeService {

	private static final Logger log = LoggerFactory.getLogger(PlatformFeeServiceImpl.class);

	private final PlatformFeePolicyRepository policies;
	private final CatalogProperties properties;
	private final AuditLogService audit;
	private final Clock clock;

	PlatformFeeServiceImpl(PlatformFeePolicyRepository policies, CatalogProperties properties, AuditLogService audit,
			Clock clock) {
		this.policies = policies;
		this.properties = properties;
		this.audit = audit;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PlatformFeeResponse> history() {
		List<PlatformFeePolicy> all = policies.findAllByOrderByEffectiveFromDescIdDesc();
		Instant now = clock.instant();
		// Mức đang áp: bản mới nhất đã tới ngày hiệu lực (cùng quy tắc với lúc publish, CatalogReadRepository.feeBpsAt).
		UUID current = all.stream().filter(policy -> !policy.getEffectiveFrom().isAfter(now)).findFirst()
				.map(PlatformFeePolicy::getId).orElse(null);
		return all.stream().map(policy -> toResponse(policy, policy.getId().equals(current))).toList();
	}

	@Override
	@Transactional
	public PlatformFeeResponse schedule(UUID adminId, PlatformFeeRequest request) {
		if (request.feeBps() < 0 || request.feeBps() > properties.maxPlatformFeeBps()) {
			throw new BusinessException(ErrorCode.FEE_OUT_OF_RANGE,
					"Phí nền tảng phải từ 0 đến " + properties.maxPlatformFeeBps() / 100 + "%");
		}
		Instant now = clock.instant();
		if (request.effectiveFrom().isBefore(now)) {
			throw new BusinessException(ErrorCode.EFFECTIVE_FROM_IN_PAST, "Ngày áp dụng không được ở quá khứ");
		}
		PlatformFeePolicy policy = policies
				.save(new PlatformFeePolicy(Uuid7.next(clock), request.feeBps(), request.effectiveFrom(), adminId));
		audit.record(adminId, AuditAction.PLATFORM_FEE_SCHEDULED, "platform_fee_policy", policy.getId(), null,
				Map.of("feeBps", policy.getFeeBps(), "effectiveFrom", policy.getEffectiveFrom().toString()));
		log.info("Admin {} đặt phí nền tảng {} bps từ {}", adminId, policy.getFeeBps(), policy.getEffectiveFrom());
		return toResponse(policy, !policy.getEffectiveFrom().isAfter(now));
	}

	private static PlatformFeeResponse toResponse(PlatformFeePolicy policy, boolean current) {
		return new PlatformFeeResponse(policy.getId(), policy.getFeeBps(), policy.getEffectiveFrom(),
				policy.getCreatedBy(), current);
	}

}
