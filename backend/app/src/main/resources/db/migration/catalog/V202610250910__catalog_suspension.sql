-- Tạm dừng bán (BR-13): lý do đình chỉ organizer và lý do tạm dừng sự kiện, hiện cho Admin / organizer.
ALTER TABLE catalog.organizers ADD COLUMN suspension_reason TEXT;
ALTER TABLE catalog.organizers_aud ADD COLUMN suspension_reason TEXT;
ALTER TABLE catalog.events ADD COLUMN suspension_reason TEXT;
