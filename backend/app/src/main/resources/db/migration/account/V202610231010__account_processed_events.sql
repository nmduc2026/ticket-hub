-- Consumer idempotent (overview.md §8.3): ghi (consumer, message_id) trong cùng transaction với xử lý; message đến lần
-- hai bị bỏ qua.
CREATE TABLE account.processed_events
(
    consumer     TEXT        NOT NULL,
    message_id   UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);
