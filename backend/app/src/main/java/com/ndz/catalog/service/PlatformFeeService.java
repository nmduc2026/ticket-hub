package com.ndz.catalog.service;

import java.util.List;
import java.util.UUID;

import com.ndz.catalog.dto.request.PlatformFeeRequest;
import com.ndz.catalog.dto.response.PlatformFeeResponse;

/** Admin đặt mức phí nền tảng (BR-06, ADR 0023): sự kiện chốt mức đang hiệu lực lúc publish. */
public interface PlatformFeeService {

	/** Lịch sử mức phí, mới nhất trước. */
	List<PlatformFeeResponse> history();

	/**
	 * Thêm mức phí mới; ghi audit. {@code FEE_OUT_OF_RANGE} khi ngoài 0–2000 bps, {@code EFFECTIVE_FROM_IN_PAST} khi mốc
	 * áp dụng trước hiện tại.
	 */
	PlatformFeeResponse schedule(UUID adminId, PlatformFeeRequest request);

}
