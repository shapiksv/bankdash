CREATE TABLE user_credential (
    id                 uuid PRIMARY KEY,
    -- Stored lowercased and trimmed, so a plain unique index is enough.
    email              varchar(254) NOT NULL,
    password_hash      varchar(100) NOT NULL,
    two_factor_enabled boolean      NOT NULL DEFAULT false,
    status             varchar(16)  NOT NULL,
    created_at         timestamptz  NOT NULL
);

CREATE UNIQUE INDEX user_credential_email_uk ON user_credential (email);

-- Opaque refresh tokens. Only the SHA-256 hash is stored. Every login starts a family;
-- each refresh replaces the presented token with a new one in the same family.
CREATE TABLE refresh_token (
    id                uuid PRIMARY KEY,
    user_id           uuid        NOT NULL REFERENCES user_credential (id),
    family_id         uuid        NOT NULL,
    token_hash        char(64)    NOT NULL,
    created_at        timestamptz NOT NULL,
    expires_at        timestamptz NOT NULL,
    family_expires_at timestamptz NOT NULL,
    replaced_by       uuid REFERENCES refresh_token (id),
    revoked           boolean     NOT NULL DEFAULT false
);

CREATE UNIQUE INDEX refresh_token_hash_uk ON refresh_token (token_hash);
CREATE INDEX refresh_token_family_idx ON refresh_token (family_id);
CREATE INDEX refresh_token_user_idx ON refresh_token (user_id);
