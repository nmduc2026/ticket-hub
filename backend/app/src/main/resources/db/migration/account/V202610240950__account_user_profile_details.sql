-- Hồ sơ cá nhân do app giữ (overview.md §6.6, màn #19): ảnh đại diện, SĐT, ngày sinh, tỉnh / thành. Email, tên, mật
-- khẩu vẫn do Keycloak giữ. province_id trỏ tới đơn vị hành chính cấp 1 của Catalog (không có khoá ngoại vì khác
-- module; Account kiểm qua CatalogApi). deleted_at: tài khoản đã xoá, hồ sơ đã ẩn danh.
ALTER TABLE account.user_profiles
    ADD COLUMN avatar_url    TEXT,
    ADD COLUMN phone         TEXT,
    ADD COLUMN date_of_birth DATE,
    ADD COLUMN province_id   UUID,
    ADD COLUMN deleted_at    TIMESTAMP WITH TIME ZONE;
