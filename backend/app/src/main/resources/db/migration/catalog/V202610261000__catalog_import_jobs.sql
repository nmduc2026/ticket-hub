-- Mỗi lần import Excel là một bản ghi (overview.md §6.1); file gốc và file lỗi lưu ở object storage.
CREATE TABLE catalog.import_jobs
(
    id             UUID        NOT NULL,
    type           TEXT        NOT NULL CHECK (type IN ('ADMINISTRATIVE_UNITS', 'VENUES', 'SEAT_MAPS')),
    file_key       TEXT        NOT NULL,
    dry_run        BOOLEAN     NOT NULL,
    status         TEXT        NOT NULL CHECK (status IN ('VALIDATING', 'VALID', 'INVALID', 'IMPORTED', 'FAILED')),
    total_rows     INTEGER     NOT NULL DEFAULT 0,
    valid_rows     INTEGER     NOT NULL DEFAULT 0,
    error_rows     INTEGER     NOT NULL DEFAULT 0,
    error_file_key TEXT,
    created_by     UUID        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id)
);

-- Địa điểm khớp theo mã khi import lại; mã tuỳ chọn nên chỉ duy nhất khi có giá trị.
ALTER TABLE catalog.venues
    ADD COLUMN code TEXT;
CREATE UNIQUE INDEX uq_venues_code ON catalog.venues (code) WHERE code IS NOT NULL;
