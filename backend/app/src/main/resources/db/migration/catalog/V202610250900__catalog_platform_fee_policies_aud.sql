-- Envers: lịch sử thay đổi mức phí nền tảng (BR-06) kèm người thao tác qua shared.revinfo.
CREATE TABLE catalog.platform_fee_policies_aud
(
    id             UUID     NOT NULL,
    rev            INTEGER  NOT NULL REFERENCES shared.revinfo (rev),
    revtype        SMALLINT,
    fee_bps        INTEGER,
    effective_from TIMESTAMPTZ,
    PRIMARY KEY (id, rev)
);
