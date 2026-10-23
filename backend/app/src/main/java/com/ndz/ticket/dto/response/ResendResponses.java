package com.ndz.ticket.dto.response;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Kết quả gửi lại email vé hàng loạt (#27, overview.md §11.1). */
public final class ResendResponses {

	private ResendResponses() {
	}

	@Schema(name = "ResendTicketsResult")
	public record BulkResult(List<BulkItem> results) {
	}

	@Schema(name = "ResendTicketsItem")
	public record BulkItem(UUID bookingId, boolean ok, String code) {
	}

}
