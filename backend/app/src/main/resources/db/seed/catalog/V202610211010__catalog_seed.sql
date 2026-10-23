-- Dữ liệu mẫu cho máy dev và test (profile `seed`), KHÔNG chạy ở môi trường thật.
-- Mã đơn vị hành chính ở đây là mã mẫu, không phải danh mục chính thức (danh mục thật nhập bằng import Excel, Sprint 6).

INSERT INTO catalog.administrative_units (id, code, name, type, level, parent_id, is_active, effective_from)
VALUES ('00000000-0000-7000-8000-0000000a0001', 'TEST-79', 'Thành phố Hồ Chí Minh', 'THANH_PHO', 1, NULL, TRUE,
        '2025-07-01');

INSERT INTO catalog.administrative_units (id, code, name, type, level, parent_id, is_active, effective_from)
VALUES ('00000000-0000-7000-8000-0000000a0101', 'TEST-79-001', 'Phường Bến Nghé', 'PHUONG', 2,
        '00000000-0000-7000-8000-0000000a0001', TRUE, '2025-07-01'),
       ('00000000-0000-7000-8000-0000000a0102', 'TEST-79-002', 'Phường Sài Gòn', 'PHUONG', 2,
        '00000000-0000-7000-8000-0000000a0001', TRUE, '2025-07-01'),
       ('00000000-0000-7000-8000-0000000a0103', 'TEST-79-003', 'Phường Tân Định', 'PHUONG', 2,
        '00000000-0000-7000-8000-0000000a0001', TRUE, '2025-07-01');

INSERT INTO catalog.venues (id, name, address, ward_id, capacity)
VALUES ('00000000-0000-7000-8000-0000000b0001', 'Sài Gòn Arena', '1 Đường Mẫu, Phường Bến Nghé',
        '00000000-0000-7000-8000-0000000a0101', 500);

INSERT INTO catalog.seat_maps (id, venue_id, name, version, width, height, locked)
VALUES ('00000000-0000-7000-8000-0000000c0001', '00000000-0000-7000-8000-0000000b0001', 'Sơ đồ chính', 1, 1200, 800,
        FALSE);

INSERT INTO catalog.sections (id, seat_map_id, code, name, shape_json)
VALUES ('00000000-0000-7000-8000-0000000d0001', '00000000-0000-7000-8000-0000000c0001', 'A', 'Khu A',
        '{"kind":"GRID","x":60,"y":160,"rotation":0,"rows":8,"seatsPerRow":20,"curve":0,"rowGap":26,"seatGap":24,"numbering":"LTR","hiddenSeats":[]}'),
       ('00000000-0000-7000-8000-0000000d0002', '00000000-0000-7000-8000-0000000c0001', 'B', 'Khu B',
        '{"kind":"GRID","x":640,"y":160,"rotation":0,"rows":10,"seatsPerRow":20,"curve":0,"rowGap":26,"seatGap":24,"numbering":"LTR","hiddenSeats":[]}'),
       ('00000000-0000-7000-8000-0000000d0003', '00000000-0000-7000-8000-0000000c0001', 'C', 'Khu C',
        '{"kind":"GRID","x":350,"y":480,"rotation":0,"rows":7,"seatsPerRow":20,"curve":0,"rowGap":26,"seatGap":24,"numbering":"LTR","hiddenSeats":[]}');

INSERT INTO catalog.seat_map_elements (id, seat_map_id, type, label, shape_json)
VALUES ('00000000-0000-7000-8000-0000000e0001', '00000000-0000-7000-8000-0000000c0001', 'STAGE', 'Sân khấu',
        '{"x":350,"y":40,"w":500,"h":60,"rotation":0}');

-- 8 + 10 + 7 hàng, mỗi hàng 20 ghế = 500 ghế; nhãn hàng A, B, C… theo thứ tự.
INSERT INTO catalog.seats (id, section_id, row_label, seat_number, x, y)
SELECT gen_random_uuid(), s.id, chr(64 + r.n), c.n, g.x0 + (c.n - 1) * 24, g.y0 + (r.n - 1) * 26
FROM (VALUES ('A', 8, 20, 60, 160), ('B', 10, 20, 640, 160), ('C', 7, 20, 350, 480)) AS g(code, row_count, per_row, x0, y0)
         JOIN catalog.sections s ON s.code = g.code AND s.seat_map_id = '00000000-0000-7000-8000-0000000c0001'
         CROSS JOIN LATERAL generate_series(1, g.row_count) AS r(n)
         CROSS JOIN LATERAL generate_series(1, g.per_row) AS c(n);

-- Organizer test (user ORGANIZER trong realm tickethub): đã duyệt, đã sẵn sàng nhận tiền (BR-05).
INSERT INTO catalog.organizers (id, display_name, status, approved_at, payout_ready)
VALUES ('00000000-0000-7000-8000-000000000002', 'Ban Tổ Chức', 'APPROVED', now(), TRUE);

-- Mức phí hiện hành 5% (BR-06), do Admin test tạo.
INSERT INTO catalog.platform_fee_policies (id, fee_bps, effective_from, created_by)
VALUES ('00000000-0000-7000-8000-0000000f0001', 500, '2026-01-01T00:00:00Z', '00000000-0000-7000-8000-000000000003');
