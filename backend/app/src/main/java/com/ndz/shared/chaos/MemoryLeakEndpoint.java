package com.ndz.shared.chaos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint rò bộ nhớ để diễn tập sự cố C8 (overview.md §20.3): giữ lại các khối byte để heap tăng liên tục, lấy heap dump
 * và phân tích. Chỉ tồn tại ở profile {@code chaos} (không bao giờ bật ở staging thường hay production) và chỉ ADMIN
 * gọi được.
 */
@Profile("chaos")
@RestController
@RequestMapping("/api/v1/chaos/memory")
@PreAuthorize("hasRole('ADMIN')")
class MemoryLeakEndpoint {

	private static final int MAX_MEGABYTES = 512;

	private final List<byte[]> leaked = new ArrayList<>();
	private final ReentrantLock lock = new ReentrantLock();

	/** Giữ thêm {@code megabytes} MB trong heap (tối đa 512 MB mỗi lần). */
	@PostMapping("/leak")
	Map<String, Object> leak(@RequestParam(defaultValue = "16") int megabytes) {
		int size = Math.min(Math.max(megabytes, 1), MAX_MEGABYTES);
		byte[] block = new byte[size * 1024 * 1024];
		// Chạm vào từng trang để bộ nhớ thật sự được cấp, không chỉ là địa chỉ ảo.
		for (int i = 0; i < block.length; i += 4096) {
			block[i] = 1;
		}
		lock.lock();
		try {
			leaked.add(block);
			return Map.of("leakedMegabytes", leaked.stream().mapToLong(b -> b.length).sum() / (1024 * 1024));
		}
		finally {
			lock.unlock();
		}
	}

	/** Thả toàn bộ phần đã giữ (sau khi diễn tập xong). */
	@DeleteMapping("/leak")
	Map<String, Object> release() {
		lock.lock();
		try {
			leaked.clear();
		}
		finally {
			lock.unlock();
		}
		return Map.of("leakedMegabytes", 0);
	}

}
