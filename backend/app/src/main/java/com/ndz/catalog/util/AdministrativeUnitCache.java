package com.ndz.catalog.util;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ndz.catalog.config.CatalogProperties;
import com.ndz.catalog.dto.response.CatalogViews.UnitRow;

/**
 * Cache trong bộ nhớ cho danh mục tỉnh / xã công khai (overview.md §11.2). Danh mục hiếm đổi nhưng mọi trang có dropdown
 * thành phố đều gọi, nên chỉ cần giữ ngắn hạn. Chỉ lưu truy vấn không có từ khoá (số khoá bị chặn bởi số tỉnh);
 * truy vấn có từ khoá luôn đọc DB. Admin đổi danh mục thì xoá cache của pod đó sau khi commit; pod khác tự hết hạn theo TTL.
 */
@Component
public class AdministrativeUnitCache {

	private record Key(Integer level, UUID parentId) {
	}

	private record Entry(List<UnitRow> rows, Instant expiresAt) {
	}

	private final Map<Key, Entry> entries = new ConcurrentHashMap<>();
	private final CatalogProperties properties;
	private final Clock clock;

	public AdministrativeUnitCache(CatalogProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	public List<UnitRow> get(Integer level, UUID parentId, Supplier<List<UnitRow>> loader) {
		Key key = new Key(level, parentId);
		Entry cached = entries.get(key);
		Instant now = clock.instant();
		if (cached != null && now.isBefore(cached.expiresAt())) {
			return cached.rows();
		}
		List<UnitRow> rows = List.copyOf(loader.get());
		entries.put(key, new Entry(rows, now.plus(properties.administrativeUnitCacheTtl())));
		return rows;
	}

	/** Xoá cache sau khi transaction ghi danh mục commit, để request kế tiếp không nạp lại dữ liệu cũ. */
	public void evictAfterCommit() {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			entries.clear();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				entries.clear();
			}
		});
	}

}
