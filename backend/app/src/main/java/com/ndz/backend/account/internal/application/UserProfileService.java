package com.ndz.backend.account.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.backend.account.internal.domain.UserProfile;
import com.ndz.backend.account.internal.persistence.UserProfileRepository;
import com.ndz.backend.account.internal.persistence.UserProfileWriter;
import com.ndz.backend.shared.BusinessException;
import com.ndz.backend.shared.CurrentUser;

@Service
public class UserProfileService {

	private static final int MAX_CACHED_USERS = 10_000;

	private final UserProfileRepository repository;
	private final UserProfileWriter writer;
	private final Clock clock;

	/** Claim lần ghi gần nhất của từng người; giống nhau thì bỏ qua DB (overview.md §6.6). LRU, giới hạn kích thước. */
	private final Map<UUID, CurrentUser> lastSynced = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<UUID, CurrentUser> eldest) {
			return size() > MAX_CACHED_USERS;
		}
	};

	UserProfileService(UserProfileRepository repository, UserProfileWriter writer, Clock clock) {
		this.repository = repository;
		this.writer = writer;
		this.clock = clock;
	}

	/**
	 * Tạo hồ sơ ở request đầu tiên; sau đó chỉ ghi khi claim đổi hoặc có lần đăng nhập mới. Không dùng transaction:
	 * upsert là một câu lệnh nguyên tử, và cache chỉ được cập nhật sau khi câu lệnh thành công.
	 */
	public void sync(CurrentUser user) {
		synchronized (lastSynced) {
			if (user.equals(lastSynced.get(user.id()))) {
				return;
			}
		}
		Instant now = clock.instant();
		writer.upsert(user, user.authTime() != null ? user.authTime() : now, now);
		synchronized (lastSynced) {
			lastSynced.put(user.id(), user);
		}
	}

	@Transactional(readOnly = true)
	public UserProfile get(UUID userId) {
		return repository.findById(userId).orElseThrow(
				() -> new BusinessException("PROFILE_NOT_FOUND", "Chưa có hồ sơ người dùng", HttpStatus.NOT_FOUND));
	}

}
