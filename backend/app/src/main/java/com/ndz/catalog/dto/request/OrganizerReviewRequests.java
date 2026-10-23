package com.ndz.catalog.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request của Admin khi duyệt hồ sơ organizer. */
public final class OrganizerReviewRequests {

	private OrganizerReviewRequests() {
	}

	/** Lý do từ chối bắt buộc: organizer thấy lý do này trong thông báo và email. */
	@Schema(name = "RejectOrganizerRequest")
	public record RejectRequest(@NotBlank @Size(max = 1000) String reason) {
	}

	@Schema(name = "BulkApproveOrganizersRequest")
	public record BulkApproveRequest(@NotEmpty @Size(max = 100) List<UUID> ids) {
	}

}
