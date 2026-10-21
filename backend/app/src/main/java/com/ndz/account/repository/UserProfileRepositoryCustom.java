package com.ndz.account.repository;

import java.time.Instant;

import com.ndz.shared.util.CurrentUser;

/** Phần của {@link UserProfileRepository} viết bằng SQL: upsert một câu lệnh nguyên tử. */
public interface UserProfileRepositoryCustom {

	/** Tạo hồ sơ nếu chưa có, chỉ ghi khi claim khác bản ghi (ADR 0016). */
	void upsert(CurrentUser user, Instant lastLoginAt, Instant now);

}
