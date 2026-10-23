-- Bản sao hồ sơ từ Keycloak (ADR 0016, overview.md §6.6); Keycloak vẫn là nguồn sự thật.
CREATE TABLE account.user_profiles
(
    user_id       UUID                     NOT NULL,
    email         TEXT,
    full_name     TEXT,
    username      TEXT,
    roles         TEXT[]                   NOT NULL DEFAULT '{}',
    enabled       BOOLEAN                  NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id)
);

CREATE INDEX user_profiles_email_idx ON account.user_profiles (lower(email));
