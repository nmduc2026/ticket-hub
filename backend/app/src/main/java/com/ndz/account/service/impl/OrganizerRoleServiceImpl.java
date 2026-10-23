package com.ndz.account.service.impl;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.account.client.KeycloakAdminClient;
import com.ndz.account.repository.AccountProcessedEventRepository;
import com.ndz.account.repository.UserProfileRepository;
import com.ndz.account.service.OrganizerRoleService;
import com.ndz.catalog.event.OrganizerApproved;

@Service
public class OrganizerRoleServiceImpl implements OrganizerRoleService {

	private static final Logger log = LoggerFactory.getLogger(OrganizerRoleServiceImpl.class);

	private static final String CONSUMER = "account.organizer-approved";
	private static final String ORGANIZER_ROLE = "ORGANIZER";

	private final AccountProcessedEventRepository processed;
	private final KeycloakAdminClient keycloak;
	private final UserProfileRepository profiles;
	private final Clock clock;

	OrganizerRoleServiceImpl(AccountProcessedEventRepository processed, KeycloakAdminClient keycloak,
			UserProfileRepository profiles, Clock clock) {
		this.processed = processed;
		this.keycloak = keycloak;
		this.profiles = profiles;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void onOrganizerApproved(OrganizerApproved event) {
		if (!processed.markProcessed(CONSUMER, event.messageId())) {
			log.info("Bỏ qua OrganizerApproved đã xử lý {}", event.messageId());
			return;
		}
		// Keycloak lỗi → ném lỗi → rollback cả dấu đã xử lý, event được gửi lại (Modulith).
		keycloak.grantRealmRole(event.organizerId(), ORGANIZER_ROLE);
		// Role có hiệu lực trong token sau lần refresh kế tiếp (<= 5 phút); hồ sơ cập nhật ngay.
		profiles.addRole(event.organizerId(), ORGANIZER_ROLE, clock.instant());
		log.info("Đã gán role ORGANIZER cho {}", event.organizerId());
	}

}
