package com.ndz.account.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.account.service.OrganizerRoleService;
import com.ndz.catalog.event.OrganizerApproved;

/** Nghe {@code OrganizerApproved} của Catalog (overview.md §7.6); xử lý nằm ở {@link OrganizerRoleService}. */
@Component
class OrganizerApprovedListener {

	private final OrganizerRoleService roles;

	OrganizerApprovedListener(OrganizerRoleService roles) {
		this.roles = roles;
	}

	@ApplicationModuleListener
	void on(OrganizerApproved event) {
		roles.onOrganizerApproved(event);
	}

}
