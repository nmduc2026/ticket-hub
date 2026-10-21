package com.ndz.shared.idempotency;

/** Bản ghi hiện có của một key; {@code response} chỉ có khi {@code completed}. */
record IdempotencyRecord(String requestHash, boolean completed, StoredResponse response) {
}
