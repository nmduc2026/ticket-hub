package com.ndz.ticket.dto.response;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.ndz.ticket.entity.enums.TicketStatus;

/** Response của API check-in (overview.md §7.5, §11.2). */
public final class CheckInResponses {

	private CheckInResponses() {
	}

	@Schema(name = "CheckIn")
	public record CheckInResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID ticketId,
			@Schema(description = "Nhãn ghế để người soát vé đối chiếu, vd. A-B-12 (khu A, hàng B, ghế 12)",
					requiredMode = Schema.RequiredMode.REQUIRED) String seat,
			@Schema(description = "Luôn là USED khi check-in thành công", requiredMode = Schema.RequiredMode.REQUIRED)
			TicketStatus status) {
	}

	@Schema(name = "SigningKeySet", description = "JWKS các khoá công khai ES256 (P-256) để xác minh QR theo kid")
	public record KeySetResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SigningKey> keys) {
	}

	@Schema(name = "SigningKey")
	public record SigningKey(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String kid,
			@Schema(description = "Luôn là EC", requiredMode = Schema.RequiredMode.REQUIRED) String kty,
			@Schema(description = "Luôn là P-256", requiredMode = Schema.RequiredMode.REQUIRED) String crv,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String x,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String y) {
	}

}
