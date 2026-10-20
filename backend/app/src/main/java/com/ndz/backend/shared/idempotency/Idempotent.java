package com.ndz.backend.shared.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu endpoint bắt buộc header {@code Idempotency-Key} (overview.md §6.7, §11.1, ADR 0025). Filter tra key
 * trước khi controller chạy; gửi lại cùng key và cùng body thì nhận lại đúng response lần đầu.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {

	/** Tên thao tác (vd. {@code create-booking}); key chỉ có nghĩa trong cùng scope và cùng người dùng. */
	String scope();

}
