-- Thông tin cho khách (BR-14: thông tin hiển thị, sửa được sau publish): giờ mở cửa, độ tuổi tối thiểu, quy định.
ALTER TABLE catalog.events
    ADD COLUMN doors_open_at   TIMESTAMPTZ,
    ADD COLUMN age_restriction SMALLINT CHECK (age_restriction BETWEEN 1 AND 99),
    ADD COLUMN rules           TEXT;
