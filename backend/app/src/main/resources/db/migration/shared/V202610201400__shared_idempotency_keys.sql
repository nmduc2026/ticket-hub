-- Idempotency-Key dùng chung (ADR 0025, overview.md §6.7). Bản ghi giữ 24 giờ rồi job dọn.
CREATE TABLE shared.idempotency_keys
(
    scope                 TEXT                     NOT NULL,
    user_id               UUID                     NOT NULL,
    key                   TEXT                     NOT NULL,
    request_hash          TEXT                     NOT NULL,
    status                TEXT                     NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    response_status       INT,
    response_body         TEXT,
    response_content_type TEXT,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (scope, user_id, key)
);

CREATE INDEX idempotency_keys_expires_at_idx ON shared.idempotency_keys (expires_at);

-- Khoá cho các job định kỳ (ShedLock): mỗi job chỉ chạy trên một instance tại một thời điểm.
CREATE TABLE shared.shedlock
(
    name       VARCHAR(64)              NOT NULL,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by  VARCHAR(255)             NOT NULL,
    PRIMARY KEY (name)
);
