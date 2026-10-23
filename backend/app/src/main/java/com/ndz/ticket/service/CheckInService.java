package com.ndz.ticket.service;

import java.util.UUID;

import com.ndz.ticket.dto.response.CheckInResponses.CheckInResponse;
import com.ndz.ticket.dto.response.CheckInResponses.KeySetResponse;

/** Check-in tại cổng (overview.md §7.5, BR-09, ADR 0024): chỉ vào khi server xác nhận. */
public interface CheckInService {

	/**
	 * Check-in một vé bằng nội dung QR. Chữ ký sai hoặc vé không tồn tại → {@code TICKET_NOT_FOUND}; organizer không
	 * sở hữu sự kiện → {@code NOT_EVENT_OWNER} (kiểm trước khi tiết lộ trạng thái vé); vé đã dùng →
	 * {@code TICKET_ALREADY_USED}; vé đã thu hồi → {@code TICKET_REVOKED}. {@code VALID → USED} nguyên tử nên hai lần
	 * quét đồng thời chỉ một lần thành công.
	 *
	 * @throws com.ndz.shared.exception.BusinessException các mã trên
	 */
	CheckInResponse checkIn(UUID organizerUserId, String qrToken, String gate);

	/** JWKS các khoá công khai (ACTIVE và RETIRED) để app check-in loại vé giả ngay tại chỗ. */
	KeySetResponse signingKeys();

}
