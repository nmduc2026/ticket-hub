package com.ndz.queue.service;

/** Điều tiết phòng chờ: gọi người vào khi còn chỗ (overview.md §7.1, BR-04). */
public interface QueueAdmissionService {

	/**
	 * Một nhịp điều tiết cho mọi sự kiện đang có phòng chờ: sự kiện đã tới giờ mở bán thì gọi tối đa N người khi số
	 * người đang ở trong còn dưới {@code max_active}. Trước giờ mở bán không gọi ai.
	 *
	 * @return số người được gọi trong nhịp này
	 */
	int admitWaiting();

}
