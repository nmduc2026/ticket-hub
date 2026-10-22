package com.ndz.payment.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Giữ tốc độ gọi Stripe không quá {@code permitsPerSecond} (hoàn tiền hàng loạt khi huỷ sự kiện, dưới bulkhead 20 của
 * overview.md §15.1). Chờ bằng {@code sleep} trên virtual thread; khoá bằng {@link ReentrantLock} để không pin thread.
 */
public final class RefundPacer {

	private final long intervalNanos;
	private final ReentrantLock lock = new ReentrantLock();
	private long nextSlot = System.nanoTime();

	public RefundPacer(int permitsPerSecond) {
		this.intervalNanos = TimeUnit.SECONDS.toNanos(1) / Math.max(permitsPerSecond, 1);
	}

	/** Chờ tới lượt kế tiếp. */
	public void acquire() throws InterruptedException {
		long wait;
		lock.lock();
		try {
			long now = System.nanoTime();
			long slot = Math.max(nextSlot, now);
			nextSlot = slot + intervalNanos;
			wait = slot - now;
		}
		finally {
			lock.unlock();
		}
		if (wait > 0) {
			TimeUnit.NANOSECONDS.sleep(wait);
		}
	}

}
