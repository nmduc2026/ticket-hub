-- Khử trùng consumer của Catalog (overview.md §8.3), vd. OrganizerPayoutStatusChanged từ Payment.
CREATE TABLE catalog.processed_events
(
    consumer     TEXT        NOT NULL,
    message_id   UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);
