-- Schema notification (overview.md §6.5): mẫu thông báo, nhật ký gửi và khử trùng event.
-- Hộp thư trong app (`notifications`, `notification_preferences`) làm ở sprint sau.

-- Mẫu theo (mã, kênh). `body_template` và `subject` là mẫu Thymeleaf (HTML cho thân, TEXT cho tiêu đề; tiêu đề dùng
-- [(${...})] để không escape HTML).
CREATE TABLE notification.templates
(
    code          TEXT NOT NULL,
    channel       TEXT NOT NULL CHECK (channel IN ('EMAIL', 'PUSH')),
    subject       TEXT NOT NULL,
    body_template TEXT NOT NULL,
    PRIMARY KEY (code, channel)
);

INSERT INTO notification.templates (code, channel, subject, body_template)
VALUES ('TICKET_ISSUED', 'EMAIL', 'Vé của bạn cho [(${eventTitle})]', $body$<!DOCTYPE html>
<html lang="vi">
<body style="font-family: Arial, sans-serif; color: #1f2937;">
  <h2>Vé của bạn đã sẵn sàng</h2>
  <p><strong th:text="${eventTitle}">Tên sự kiện</strong></p>
  <p>
    Thời gian: <span th:text="${startsAt}">01/01/2027 19:00</span><br/>
    Địa điểm: <span th:text="${venueName}">Địa điểm</span>
  </p>
  <p>Bạn có <span th:text="${tickets.size()}">1</span> vé:</p>
  <ul>
    <li th:each="ticket : ${tickets}"><span th:text="${ticket.seat}">Khu A · Hàng B · Ghế 1</span>
      (<span th:text="${ticket.tier}">VIP</span>)</li>
  </ul>
  <p>Mở mã QR của từng vé tại <a th:href="${ticketsUrl}" th:text="${ticketsUrl}">Vé của tôi</a> để vào cổng.</p>
  <p style="color: #6b7280; font-size: 12px;">Ticket Hub</p>
</body>
</html>$body$);

-- Mỗi lần gửi một dòng; lỗi gửi ghi FAILED kèm lý do (không ghi nội dung thư hay email người nhận).
CREATE TABLE notification.notification_log
(
    id                UUID        NOT NULL,
    user_id           UUID        NOT NULL,
    channel           TEXT        NOT NULL CHECK (channel IN ('EMAIL', 'PUSH')),
    template_code     TEXT        NOT NULL,
    source_message_id UUID        NOT NULL, -- event nguồn, để lần theo khi gửi lỗi
    status            TEXT        NOT NULL CHECK (status IN ('SENT', 'FAILED')),
    sent_at           TIMESTAMPTZ,
    error             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);
CREATE INDEX ix_notification_log_user ON notification.notification_log (user_id, created_at DESC);
CREATE INDEX ix_notification_log_source ON notification.notification_log (source_message_id);

-- Consumer idempotent: ghi (consumer, message_id) trong cùng transaction với xử lý.
CREATE TABLE notification.processed_events
(
    consumer     TEXT        NOT NULL,
    message_id   UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);
