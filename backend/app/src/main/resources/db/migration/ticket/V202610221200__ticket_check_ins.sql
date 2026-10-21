-- Nhật ký check-in (overview.md §6.4, §7.5): mỗi vé tối đa một lần vào cổng. UNIQUE (ticket_id) là chốt chặn thứ hai sau
-- câu lệnh UPDATE ... WHERE status = 'VALID'.
CREATE TABLE ticket.check_ins
(
    id         UUID        NOT NULL,
    ticket_id  UUID        NOT NULL REFERENCES ticket.tickets (id),
    gate       TEXT        NOT NULL,
    scanned_by UUID        NOT NULL,
    scanned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_check_ins_ticket UNIQUE (ticket_id)
);
