package com.ndz.backend.catalog.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request của hạng vé và việc gán hạng vé cho khu ghế. */
public final class TierRequests {

	private TierRequests() {
	}

	@Schema(name = "TicketTierRequest")
	public record TierRequest(
			@NotBlank @Size(max = 100) String name,
			@Schema(description = "Đơn vị nhỏ nhất của tiền tệ; VND không nhân 100 (50.000đ là 50000)")
			@Min(0) long priceMinor) {
	}

	@Schema(name = "TierSectionAssignmentRequest")
	public record AssignmentRequest(@NotNull UUID sectionId, @NotNull UUID tierId) {
	}

	@Schema(name = "TierSectionsRequest", description = "Trạng thái mới của việc gán: khu không có trong danh sách thì không bán")
	public record AssignmentsRequest(@NotNull @Valid List<AssignmentRequest> assignments) {
	}

}
