-- Hồ sơ đăng ký organizer (overview.md §6.1, §7.6; prototype #22). Hàng đã có (organizer seed) để trống các cột hồ sơ.
-- `version` cho khoá lạc quan khi Admin duyệt / từ chối đồng thời.
ALTER TABLE catalog.organizers
    ADD COLUMN organizer_type TEXT CHECK (organizer_type IN ('INDIVIDUAL', 'BUSINESS')),
    ADD COLUMN legal_name     TEXT, -- tên doanh nghiệp, hoặc họ tên với cá nhân
    ADD COLUMN legal_id       TEXT, -- mã số thuế (doanh nghiệp) hoặc số CCCD (cá nhân)
    ADD COLUMN contact_email  TEXT,
    ADD COLUMN contact_phone  TEXT,
    ADD COLUMN website        TEXT,
    ADD COLUMN introduction   TEXT,
    ADD COLUMN event_scale    TEXT,
    ADD COLUMN submitted_at   TIMESTAMPTZ,
    ADD COLUMN version        BIGINT NOT NULL DEFAULT 0;

-- Admin lọc danh sách hồ sơ theo trạng thái, mới nộp trước.
CREATE INDEX ix_organizers_status_submitted ON catalog.organizers (status, submitted_at DESC);
