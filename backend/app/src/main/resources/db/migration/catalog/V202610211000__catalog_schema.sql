-- Schema catalog (overview.md §6.1): tổ chức, địa điểm, sơ đồ ghế, sự kiện, hạng vé, mức phí nền tảng.
-- Khoá chính UUIDv7 do app sinh; bảng có cập nhật đồng thời có cột `version` (optimistic lock).
-- Ràng buộc bất biến đặt ở DB (CHECK, UNIQUE, FK), không chỉ ở code.

CREATE TABLE catalog.organizers
(
    id               UUID        NOT NULL, -- = id người dùng trong Keycloak
    display_name     TEXT        NOT NULL,
    status           TEXT        NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    approved_at      TIMESTAMPTZ,
    rejection_reason TEXT,
    payout_ready     BOOLEAN     NOT NULL DEFAULT FALSE, -- bản sao charges_enabled từ Payment (BR-05, BR-13)
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);

-- Một bảng cho cả 2 cấp: cấp 1 Tỉnh / Thành phố, cấp 2 Xã / Phường / Đặc khu.
CREATE TABLE catalog.administrative_units
(
    id             UUID     NOT NULL,
    code           TEXT     NOT NULL UNIQUE,
    name           TEXT     NOT NULL,
    type           TEXT     NOT NULL CHECK (type IN ('TINH', 'THANH_PHO', 'PHUONG', 'XA', 'DAC_KHU')),
    level          SMALLINT NOT NULL CHECK (level IN (1, 2)),
    parent_id      UUID REFERENCES catalog.administrative_units (id),
    is_active      BOOLEAN  NOT NULL DEFAULT TRUE,
    effective_from DATE     NOT NULL,
    version        BIGINT   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_admin_units_level_parent CHECK (
        (level = 1 AND parent_id IS NULL) OR (level = 2 AND parent_id IS NOT NULL)),
    CONSTRAINT ck_admin_units_level_type CHECK (
        (level = 1 AND type IN ('TINH', 'THANH_PHO')) OR (level = 2 AND type IN ('PHUONG', 'XA', 'DAC_KHU')))
);
CREATE INDEX ix_admin_units_parent ON catalog.administrative_units (parent_id) WHERE is_active;

-- Địa điểm chỉ tham chiếu cấp xã; FK không kiểm được cấp nên service kiểm khi tạo / sửa.
CREATE TABLE catalog.venues
(
    id       UUID    NOT NULL,
    name     TEXT    NOT NULL,
    address  TEXT    NOT NULL,
    ward_id  UUID    NOT NULL REFERENCES catalog.administrative_units (id),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    PRIMARY KEY (id)
);
CREATE INDEX ix_venues_ward ON catalog.venues (ward_id);

CREATE TABLE catalog.seat_maps
(
    id       UUID    NOT NULL,
    venue_id UUID    NOT NULL REFERENCES catalog.venues (id),
    name     TEXT    NOT NULL,
    version  INTEGER NOT NULL DEFAULT 1, -- phiên bản của sơ đồ (bản sao để sửa), không phải khoá optimistic
    width    INTEGER NOT NULL CHECK (width > 0),
    height   INTEGER NOT NULL CHECK (height > 0),
    locked   BOOLEAN NOT NULL DEFAULT FALSE, -- true khi đã có sự kiện publish dùng: bất biến
    PRIMARY KEY (id),
    UNIQUE (venue_id, name, version)
);

CREATE TABLE catalog.sections
(
    id          UUID  NOT NULL,
    seat_map_id UUID  NOT NULL REFERENCES catalog.seat_maps (id),
    code        TEXT  NOT NULL,
    name        TEXT  NOT NULL,
    shape_json  JSONB NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (seat_map_id, code)
);

-- Phần tử không bán: sân khấu, lối đi, nhãn, vùng đứng.
CREATE TABLE catalog.seat_map_elements
(
    id          UUID  NOT NULL,
    seat_map_id UUID  NOT NULL REFERENCES catalog.seat_maps (id),
    type        TEXT  NOT NULL CHECK (type IN ('STAGE', 'AISLE', 'LABEL', 'AREA')),
    label       TEXT,
    shape_json  JSONB NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE catalog.seats
(
    id          UUID    NOT NULL,
    section_id  UUID    NOT NULL REFERENCES catalog.sections (id),
    row_label   TEXT    NOT NULL,
    seat_number INTEGER NOT NULL CHECK (seat_number > 0),
    x           INTEGER NOT NULL,
    y           INTEGER NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (section_id, row_label, seat_number)
);

CREATE TABLE catalog.events
(
    id                   UUID        NOT NULL,
    organizer_id         UUID        NOT NULL REFERENCES catalog.organizers (id),
    venue_id             UUID REFERENCES catalog.venues (id),
    seat_map_id          UUID REFERENCES catalog.seat_maps (id),
    title                TEXT        NOT NULL,
    description          TEXT,
    category             TEXT,
    banner_url           TEXT,
    starts_at            TIMESTAMPTZ,
    ends_at              TIMESTAMPTZ,
    sale_starts_at       TIMESTAMPTZ,
    status               TEXT        NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ON_SALE', 'SUSPENDED', 'ENDED', 'CANCELLED')),
    high_demand          BOOLEAN     NOT NULL DEFAULT FALSE,
    max_tickets_per_user SMALLINT    NOT NULL DEFAULT 6 CHECK (max_tickets_per_user >= 1),
    platform_fee_bps     INTEGER CHECK (platform_fee_bps BETWEEN 0 AND 10000), -- chốt lúc publish (BR-06)
    inventory_ready      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_events_times CHECK (ends_at IS NULL OR starts_at IS NULL OR starts_at < ends_at),
    -- Nháp được thiếu thông tin; mọi trạng thái khác phải đủ (BR-05, BR-06, BR-14).
    CONSTRAINT ck_events_complete_after_draft CHECK (
        status = 'DRAFT' OR (venue_id IS NOT NULL AND seat_map_id IS NOT NULL AND starts_at IS NOT NULL
            AND ends_at IS NOT NULL AND sale_starts_at IS NOT NULL AND platform_fee_bps IS NOT NULL))
);
CREATE INDEX ix_events_organizer ON catalog.events (organizer_id);
CREATE INDEX ix_events_status_starts ON catalog.events (status, starts_at);

CREATE TABLE catalog.ticket_tiers
(
    id          UUID    NOT NULL,
    event_id    UUID    NOT NULL REFERENCES catalog.events (id),
    name        TEXT    NOT NULL,
    price_minor BIGINT  NOT NULL CHECK (price_minor >= 0), -- BR-10: số nguyên đơn vị nhỏ nhất
    currency    CHAR(3) NOT NULL DEFAULT 'VND',
    PRIMARY KEY (id),
    UNIQUE (event_id, id) -- đích của FK ghép từ tier_sections
);
CREATE INDEX ix_ticket_tiers_event ON catalog.ticket_tiers (event_id);

-- Mỗi khu thuộc tối đa 1 hạng vé trong 1 sự kiện; hạng vé phải cùng sự kiện với dòng gán.
CREATE TABLE catalog.tier_sections
(
    event_id   UUID NOT NULL,
    tier_id    UUID NOT NULL,
    section_id UUID NOT NULL REFERENCES catalog.sections (id),
    PRIMARY KEY (event_id, section_id),
    FOREIGN KEY (event_id, tier_id) REFERENCES catalog.ticket_tiers (event_id, id)
);
CREATE INDEX ix_tier_sections_tier ON catalog.tier_sections (tier_id);

CREATE TABLE catalog.platform_fee_policies
(
    id             UUID        NOT NULL,
    fee_bps        INTEGER     NOT NULL CHECK (fee_bps BETWEEN 0 AND 10000),
    effective_from TIMESTAMPTZ NOT NULL,
    created_by     UUID        NOT NULL,
    PRIMARY KEY (id)
);
