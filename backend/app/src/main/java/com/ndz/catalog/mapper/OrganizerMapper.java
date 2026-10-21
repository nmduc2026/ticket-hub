package com.ndz.catalog.mapper;

import org.springframework.stereotype.Component;

import com.ndz.catalog.dto.response.OrganizerResponses.AdminOrganizer;
import com.ndz.catalog.dto.response.OrganizerResponses.Application;
import com.ndz.catalog.entity.Organizer;

@Component
public class OrganizerMapper {

	public Application toApplication(Organizer organizer) {
		return new Application(organizer.getStatus(), organizer.getSubmittedAt(), organizer.getRejectionReason(),
				organizer.getOrganizerType(), organizer.getDisplayName(), organizer.getLegalName(),
				organizer.getLegalId(), organizer.getContactEmail(), organizer.getContactPhone(),
				organizer.getWebsite(), organizer.getIntroduction(), organizer.getEventScale());
	}

	public AdminOrganizer toAdminItem(Organizer organizer) {
		return new AdminOrganizer(organizer.getId(), organizer.getDisplayName(), organizer.getContactEmail(),
				organizer.getStatus(), organizer.getSubmittedAt(), organizer.getOrganizerType(),
				organizer.getLegalName(), organizer.getLegalId(), organizer.getContactPhone(), organizer.getWebsite(),
				organizer.getIntroduction(), organizer.getEventScale(), organizer.getRejectionReason());
	}

}
