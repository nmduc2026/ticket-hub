-- Envers: lịch sử khoá / mở khoá tài khoản (user_profiles.enabled).
CREATE TABLE account.user_profiles_aud
(
    user_id UUID    NOT NULL,
    rev     INTEGER NOT NULL REFERENCES shared.revinfo (rev),
    revtype SMALLINT,
    enabled BOOLEAN,
    PRIMARY KEY (user_id, rev)
);
