package com.ndz.shared.storage;

/** Lỗi hạ tầng khi làm việc với kho file (mạng, quyền, bucket). */
public class StorageException extends RuntimeException {

	public StorageException(String message, Throwable cause) {
		super(message, cause);
	}

}
