-- Audit (overview.md §13.4): Hibernate Envers ghi revision cho entity quản trị (`revinfo` + bảng `*_aud` của module),
-- `audit_log` ghi một dòng cho mỗi thao tác quản trị: ai, lúc nào, làm gì, trên đối tượng nào, trước / sau.
CREATE SEQUENCE shared.revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE shared.revinfo
(
    rev      INTEGER NOT NULL,
    revtstmp BIGINT  NOT NULL,
    actor_id UUID, -- người thao tác (sub của token); null khi thay đổi không đến từ một request đăng nhập
    PRIMARY KEY (rev)
);

CREATE TABLE shared.audit_log
(
    id           UUID        NOT NULL,
    actor_id     UUID        NOT NULL,
    action       TEXT        NOT NULL,
    target_type  TEXT        NOT NULL,
    target_id    TEXT        NOT NULL,
    before_state JSONB,
    after_state  JSONB,
    created_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX ix_audit_log_created_at ON shared.audit_log (created_at DESC);
CREATE INDEX ix_audit_log_target ON shared.audit_log (target_type, target_id);
