package com.ndz.account.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.account.entity.UserProfile;
import com.ndz.account.repository.UserProfileRepository;
import com.ndz.account.service.UserProfileService;
import com.ndz.shared.exception.BusinessException;
import com.ndz.shared.exception.ErrorCode;
import com.ndz.shared.util.CurrentUser;

@Service
public class UserProfileServiceImpl implements UserProfileService {

	private static final int MAX_CACHED_USERS = 10_000;

	private final UserProfileRepository repository;
	private final Clock clock;

	/** Claim lần ghi gần nhất của từng người; giống nhau thì bỏ qua DB (overview.md §6.6). LRU, giới hạn kích thước. */
	private final Map<UUID, CurrentUser> lastSynced = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<UUID, CurrentUser> eldest) {
			return size() > MAX_CACHED_USERS;
		}
	};

	UserProfileServiceImpl(UserProfileRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/**
	 * Tạo hồ sơ ở request đầu tiên; sau đó chỉ ghi khi claim đổi hoặc có lần đăng nhập mới. Không dùng transaction:
	 * upsert là một câu lệnh nguyên tử, và cache chỉ được cập nhật sau khi câu lệnh thành công.
	 */
	@Override
	public void sync(CurrentUser user) {
		synchronized (lastSynced) {
			if (user.equals(lastSynced.get(user.id()))) {
				return;
			}
		}
		Instant now = clock.instant();
		repository.upsert(user, user.authTime() != null ? user.authTime() : now, now);
		synchronized (lastSynced) {
			lastSynced.put(user.id(), user);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public UserProfile get(UUID userId) {
		return repository.findById(userId).orElseThrow(
				() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND, "Chưa có hồ sơ người dùng"));
	}

}
