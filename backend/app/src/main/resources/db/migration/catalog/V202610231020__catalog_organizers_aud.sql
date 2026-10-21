-- Envers: lịch sử thay đổi của organizers (trạng thái hồ sơ, lý do từ chối, mốc duyệt).
CREATE TABLE catalog.organizers_aud
(
    id               UUID    NOT NULL,
    rev              INTEGER NOT NULL REFERENCES shared.revinfo (rev),
    revtype          SMALLINT,
    display_name     TEXT,
    status           TEXT,
    approved_at      TIMESTAMPTZ,
    rejection_reason TEXT,
    payout_ready     BOOLEAN,
    PRIMARY KEY (id, rev)
);
