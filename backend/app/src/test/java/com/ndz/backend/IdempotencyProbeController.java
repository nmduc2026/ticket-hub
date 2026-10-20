package com.ndz.backend;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ndz.backend.shared.BusinessException;
import com.ndz.backend.shared.idempotency.Idempotent;

/** Endpoint chỉ có trong test để kiểm tra filter Idempotency-Key; {@code executions} đếm số lần controller thật sự chạy. */
@RestController
@RequestMapping("/api/v1/test-probe/idempotent")
public class IdempotencyProbeController {

	public static final AtomicInteger executions = new AtomicInteger();
	public static volatile CountDownLatch started = new CountDownLatch(1);
	public static volatile CountDownLatch release = new CountDownLatch(0);

	@PostMapping("/create")
	@Idempotent(scope = "test-create")
	@org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
	Map<String, Object> create(@RequestBody Map<String, Object> body) throws InterruptedException {
		int n = executions.incrementAndGet();
		started.countDown();
		release.await(10, TimeUnit.SECONDS);
		return Map.of("execution", n, "echo", body);
	}

	@PostMapping("/conflict")
	@Idempotent(scope = "test-conflict")
	void conflict() {
		executions.incrementAndGet();
		throw new BusinessException("SEAT_UNAVAILABLE", "Ghế vừa có người giữ", HttpStatus.CONFLICT);
	}

	@PostMapping("/boom")
	@Idempotent(scope = "test-boom")
	void boom() {
		executions.incrementAndGet();
		throw new IllegalStateException("lỗi hệ thống giả lập");
	}

	/** Không đánh dấu {@code @Idempotent}: không cần key. */
	@PostMapping("/plain")
	Map<String, Object> plain() {
		return Map.of("execution", executions.incrementAndGet());
	}

	public static void reset() {
		executions.set(0);
		started = new CountDownLatch(1);
		release = new CountDownLatch(0);
	}

}
