package com.ndz.ticket.service;

/** Kiểm tra bất biến "không bán một ghế cho hai người" và xuất ra metric (overview.md §10, §16.3). */
public interface TicketInvariantService {

	/**
	 * Chạy truy vấn ghế trùng, cập nhật gauge {@code tickethub.invariant.duplicate_seat_tickets} và trả số ghế trùng.
	 * Lớn hơn 0 thì ghi log ERROR.
	 */
	int checkDuplicateSeatTickets();

}
