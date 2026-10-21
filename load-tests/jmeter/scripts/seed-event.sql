-- Dựng sự kiện ON_SALE với 100 ghế cho kịch bản Flash sale (500 user tranh 100 ghế), trực tiếp ở schema booking.
-- Booking chỉ cần bản sao sự kiện và tồn kho ghế để giữ ghế; Payment / Ticket dùng stripe-mock và event của Booking.
-- Chạy trên DB staging hoặc dev, không bao giờ trên dữ liệu thật:
--   psql "$DATABASE_URL" -v event_id=$(uuidgen) -f seed-event.sql
--   psql "$DATABASE_URL" -c "\copy (SELECT event_id, seat_id FROM booking.seat_inventory WHERE event_id = '<event_id>') TO 'seats.csv' CSV HEADER"
-- Chạy lại với cùng event_id thì bỏ qua (ON CONFLICT DO NOTHING).

INSERT INTO booking.event_snapshots
    (event_id, organizer_id, title, venue_id, venue_name, city_id, city_name, seat_map_id, starts_at, ends_at,
     sale_starts_at, status, max_tickets_per_user, platform_fee_bps, last_event_at)
VALUES (:'event_id', gen_random_uuid(), 'Flash sale 100 ghế', gen_random_uuid(), 'Sân thử tải', gen_random_uuid(),
        'TP.HCM', gen_random_uuid(), now() + interval '30 days', now() + interval '31 days', now() - interval '1 day',
        'ON_SALE', 6, 500, now())
ON CONFLICT (event_id) DO NOTHING;

INSERT INTO booking.seat_inventory
    (event_id, seat_id, tier_id, tier_name, section_code, row_label, seat_number, price_minor)
SELECT :'event_id', gen_random_uuid(), '00000000-0000-7000-8000-00000000f001', 'Flash', 'A',
       chr(65 + (n - 1) / 20), ((n - 1) % 20) + 1, 500000
FROM generate_series(1, 100) AS n
WHERE NOT EXISTS (SELECT 1 FROM booking.seat_inventory WHERE event_id = :'event_id');

SELECT count(*) AS seats FROM booking.seat_inventory WHERE event_id = :'event_id';
