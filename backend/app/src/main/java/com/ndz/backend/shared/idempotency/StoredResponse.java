package com.ndz.backend.shared.idempotency;

/** Response đã lưu của lần xử lý đầu tiên, phát lại nguyên văn khi client gửi lại cùng key. */
record StoredResponse(int status, String body, String contentType) {
}
