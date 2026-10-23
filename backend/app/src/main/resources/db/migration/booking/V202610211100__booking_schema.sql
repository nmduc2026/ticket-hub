-- Schema booking (overview.md §6.2): bản sao sự kiện, tồn kho ghế, đơn đặt vé và khử trùng event.
-- Booking không đọc bảng của Catalog: mọi dữ liệu sự kiện đến qua event và interface công khai của Catalog.

-- Bản sao thông tin sự kiện, cập nhật từ event của Catalog. `last_event_at` là mốc của event đã áp dụng gần nhất:
-- event cũ hơn mốc này bị bỏ qua, nên event đến sai thứ tự (gửi lại sau lỗi) không ghi đè dữ liệu mới.
CREATE TABLE booking.event_snapshots
(
    event_id             UUID        NOT NULL,
    organizer_id         UUID        NOT NULL,
    title                TEXT        NOT NULL,
    description          TEXT,
    category             TEXT,
    banner_url           TEXT,
    venue_id             UUID        NOT NULL,
    venue_name           TEXT        NOT NULL,
    city_id              UUID        NOT NULL,
    city_name            TEXT        NOT NULL,
    seat_map_id          UUID        NOT NULL,
    starts_at            TIMESTAMPTZ NOT NULL,
    ends_at              TIMESTAMPTZ NOT NULL,
    sale_starts_at       TIMESTAMPTZ NOT NULL,
    status               TEXT        NOT NULL
        CHECK (status IN ('PUBLISHED', 'ON_SALE', 'SUSPENDED', 'ENDED', 'CANCELLED')),
    high_demand          BOOLEAN     NOT NULL DEFAULT FALSE,
    max_tickets_per_user SMALLINT    NOT NULL CHECK (max_tickets_per_user >= 1),
    platform_fee_bps     INTEGER     NOT NULL CHECK (platform_fee_bps BETWEEN 0 AND 10000),
    currency             CHAR(3)     NOT NULL DEFAULT 'VND',
    last_event_at        TIMESTAMPTZ NOT NULL,
    version              BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (event_id)
);
CREATE INDEX ix_event_snapshots_status_starts ON booking.event_snapshots (status, starts_at);
CREATE INDEX ix_event_snapshots_city ON booking.event_snapshots (city_id);

CREATE TABLE booking.bookings
(
    id                 UUID        NOT NULL,
    user_id            UUID        NOT NULL,
    event_id           UUID        NOT NULL REFERENCES booking.event_snapshots (event_id),
    status             TEXT        NOT NULL
        CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'CANCELLED', 'REFUND_REQUESTED', 'REFUNDED',
                          'REFUND_FAILED')),
    total_minor        BIGINT      NOT NULL CHECK (total_minor >= 0), -- BR-10
    currency           CHAR(3)     NOT NULL DEFAULT 'VND',
    expires_at         TIMESTAMPTZ NOT NULL,
    payment_started_at TIMESTAMPTZ, -- ghi khi Payment tạo PaymentIntent, dùng cho ân hạn BR-03
    contact_email      TEXT        NOT NULL, -- email của khách lúc đặt vé, dùng để gửi vé và hoá đơn
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
-- BR-02: mỗi user chỉ 1 booking đang chờ thanh toán cho mỗi sự kiện.
CREATE UNIQUE INDEX uq_one_pending_per_user ON booking.bookings (user_id, event_id) WHERE status = 'PENDING_PAYMENT';
CREATE INDEX ix_bookings_user_event ON booking.bookings (user_id, event_id);
-- Job hết hạn quét booking đang chờ thanh toán theo hạn giữ.
CREATE INDEX ix_bookings_pending_expiry ON booking.bookings (expires_at) WHERE status = 'PENDING_PAYMENT';

-- Tồn kho ghế theo sự kiện; chỉ có ghế của khu đã gán hạng vé. Cập nhật có điều kiện (`WHERE status = 'AVAILABLE'`).
CREATE TABLE booking.seat_inventory
(
    event_id     UUID    NOT NULL REFERENCES booking.event_snapshots (event_id),
    seat_id      UUID    NOT NULL,
    tier_id      UUID    NOT NULL,
    tier_name    TEXT    NOT NULL,
    section_code TEXT    NOT NULL,
    row_label    TEXT    NOT NULL,
    seat_number  INTEGER NOT NULL,
    price_minor  BIGINT  NOT NULL CHECK (price_minor >= 0),
    currency     CHAR(3) NOT NULL DEFAULT 'VND',
    status       TEXT    NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'HELD', 'SOLD')),
    booking_id   UUID REFERENCES booking.bookings (id),
    version      BIGINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (event_id, seat_id),
    -- Ghế trống không thuộc booking nào; ghế đang giữ hoặc đã bán phải thuộc đúng một booking (BR-01).
    CONSTRAINT ck_seat_inventory_owner CHECK (
        (status = 'AVAILABLE' AND booking_id IS NULL) OR (status IN ('HELD', 'SOLD') AND booking_id IS NOT NULL))
);
CREATE INDEX ix_seat_inventory_booking ON booking.seat_inventory (booking_id) WHERE booking_id IS NOT NULL;
CREATE INDEX ix_seat_inventory_event_status ON booking.seat_inventory (event_id, status);

CREATE TABLE booking.booking_items
(
    booking_id  UUID   NOT NULL REFERENCES booking.bookings (id),
    event_id    UUID   NOT NULL,
    seat_id     UUID   NOT NULL,
    tier_id     UUID   NOT NULL,
    price_minor BIGINT NOT NULL CHECK (price_minor >= 0),
    PRIMARY KEY (booking_id, seat_id),
    FOREIGN KEY (event_id, seat_id) REFERENCES booking.seat_inventory (event_id, seat_id)
);

-- Consumer idempotent: ghi (consumer, message_id) trong cùng transaction với xử lý; message đến lần hai bị bỏ qua.
CREATE TABLE booking.processed_events
(
    consumer     TEXT        NOT NULL,
    message_id   UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);
