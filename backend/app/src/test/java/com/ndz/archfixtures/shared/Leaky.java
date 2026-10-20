package com.ndz.archfixtures.shared;

import com.ndz.archfixtures.payment.Intruder;

/** Fixture cho test kiến trúc: `shared` phụ thuộc vào một module nghiệp vụ. */
public class Leaky {

	Intruder intruder = new Intruder();

}
