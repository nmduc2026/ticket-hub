-- Bản sao thông tin cho khách của sự kiện (giờ mở cửa, độ tuổi, quy định), cập nhật từ EventPublished / EventUpdated.
ALTER TABLE booking.event_snapshots
    ADD COLUMN doors_open_at   TIMESTAMPTZ,
    ADD COLUMN age_restriction SMALLINT,
    ADD COLUMN rules           TEXT;
