package com.ndz.shared.audit;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ndz.shared.util.Uuid7;

/**
 * Ghi nhật ký thao tác quản trị (overview.md §13.4). Module nào cũng gọi được. Bắt buộc chạy trong transaction của
 * thao tác đang ghi nhật ký, để thao tác và dòng nhật ký cùng thành công hoặc cùng bị hoàn tác.
 */
@Service
public class AuditLogService {

	private final AuditLogRepository logs;
	private final Clock clock;

	AuditLogService(AuditLogRepository logs, Clock clock) {
		this.logs = logs;
		this.clock = clock;
	}

	/**
	 * @param actorId người thao tác (Admin)
	 * @param targetType loại đối tượng, vd. {@code organizer}, {@code user}, {@code booking}
	 * @param before trạng thái liên quan trước thao tác, có thể null
	 * @param after trạng thái liên quan sau thao tác, có thể null
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(UUID actorId, AuditAction action, String targetType, UUID targetId,
			Map<String, Object> before, Map<String, Object> after) {
		logs.save(new AuditLog(Uuid7.next(clock), actorId, action, targetType, targetId.toString(), before, after,
				clock.instant()));
	}

}
