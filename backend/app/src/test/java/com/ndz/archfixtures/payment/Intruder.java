package com.ndz.archfixtures.payment;

import com.ndz.archfixtures.booking.internal.SeatLock;

/** Fixture cho test kiến trúc: module payment truy cập thẳng vào nội bộ của booking. */
public class Intruder {

	SeatLock lock = new SeatLock();

}
