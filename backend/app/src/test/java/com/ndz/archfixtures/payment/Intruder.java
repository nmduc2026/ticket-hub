package com.ndz.archfixtures.payment;

import com.ndz.archfixtures.booking.repository.SeatLock;

/** Fixture cho test kiến trúc: module payment truy cập thẳng vào repository của booking. */
public class Intruder {

	SeatLock lock = new SeatLock();

}
