-- Hộp thư thông báo trong app (overview.md §6.5, §11.2): tạo từ event nghiệp vụ, cùng consumer với email.
-- UNIQUE (user_id, source_event_id) làm consumer idempotent: nhận lại cùng event không tạo thêm thông báo.
CREATE TABLE notification.notifications
(
    id              UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    type            TEXT        NOT NULL,
    title           TEXT        NOT NULL,
    body            TEXT        NOT NULL,
    link            TEXT,
    source_event_id UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    read_at         TIMESTAMPTZ,
    PRIMARY KEY (id),
    CONSTRAINT uq_notifications_source UNIQUE (user_id, source_event_id)
);
-- Hộp thư mới nhất trước; id chỉ để phân định khi trùng thời điểm và làm con trỏ phân trang.
CREATE INDEX ix_notifications_user_recent ON notification.notifications (user_id, created_at DESC, id DESC);
-- Đếm số chưa đọc cho chuông.
CREATE INDEX ix_notifications_user_unread ON notification.notifications (user_id) WHERE read_at IS NULL;
-- Job xoá bản cũ hơn 90 ngày.
CREATE INDEX ix_notifications_created ON notification.notifications (created_at);

-- Cài đặt nhận thông báo theo loại. Không có dòng nghĩa là mặc định bật cả email lẫn trong app.
-- Loại bắt buộc (vé, hoàn tiền, huỷ sự kiện) không tắt được, kiểm ở service.
CREATE TABLE notification.notification_preferences
(
    user_id UUID    NOT NULL,
    type    TEXT    NOT NULL,
    email   BOOLEAN NOT NULL,
    in_app  BOOLEAN NOT NULL,
    PRIMARY KEY (user_id, type)
);
