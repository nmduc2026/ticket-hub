package com.ndz.ticket.dto.response;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.ndz.ticket.entity.enums.TicketStatus;

/** Response của API vé của tôi (overview.md §11.2). */
public final class TicketResponses {

	private TicketResponses() {
	}

	@Schema(name = "Ticket")
	public record TicketResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID ticketId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String eventTitle,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String venueName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) TicketSeat seat,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tierName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) TicketStatus status,
			@Schema(description = "JWT ES256 để vẽ mã QR; xác minh bằng khoá công khai theo kid",
					requiredMode = Schema.RequiredMode.REQUIRED) String qrToken) {
	}

	@Schema(name = "TicketSeat")
	public record TicketSeat(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sectionCode,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String row,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int number) {
	}

}
