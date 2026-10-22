package com.ndz.booking.event.listener;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.ndz.booking.event.PaymentReconcileFlagged;
import com.ndz.booking.repository.OrganizerSalesQueries;

/**
 * Ghi cờ {@code needs_reconcile} theo kết quả đối soát của Payment (overview.md §16.3). {@code @ApplicationModuleListener}
 * đã là {@code @TransactionalEventListener} (chạy sau commit của Payment, tự mở transaction mới), không thêm
 * {@code @Transactional} nữa.
 */
@Component
class PaymentReconcileFlagListener {

	private final OrganizerSalesQueries queries;

	PaymentReconcileFlagListener(OrganizerSalesQueries queries) {
		this.queries = queries;
	}

	@ApplicationModuleListener
	void on(PaymentReconcileFlagged event) {
		queries.setNeedsReconcile(event.bookingId(), event.flagged());
	}

}
