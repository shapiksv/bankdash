-- One profile per auth user, created from auth.user-registered.v1. user_id is auth's user id (the JWT sub);
-- there is no foreign key to auth, which owns its own database.
CREATE TABLE user_profile (
    user_id           uuid PRIMARY KEY,
    full_name         varchar(100) NOT NULL,
    -- Lowercase [a-z0-9._-], 3-30 characters; contacts are added by username (step 7).
    username          varchar(30)  NOT NULL,
    date_of_birth     date,
    present_address   varchar(200),
    permanent_address varchar(200),
    city              varchar(100),
    postal_code       varchar(20),
    country           varchar(100),
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL
);

CREATE UNIQUE INDEX user_profile_username_uk ON user_profile (username);

CREATE TABLE preferences (
    user_id                   uuid PRIMARY KEY REFERENCES user_profile (user_id),
    -- Display only: all money is USD (docs/architecture.md section 6).
    currency                  varchar(3)  NOT NULL,
    time_zone                 varchar(64) NOT NULL,
    notify_on_transfers       boolean     NOT NULL,
    notify_on_merchant_orders boolean     NOT NULL,
    notify_on_recommendations boolean     NOT NULL
);

-- Kept apart from user_profile so that reading a profile never loads the image. At most 1 MB.
CREATE TABLE avatar (
    user_id      uuid PRIMARY KEY REFERENCES user_profile (user_id),
    content_type varchar(32) NOT NULL,
    data         bytea       NOT NULL,
    updated_at   timestamptz NOT NULL
);

-- Idempotent consumers of libs/messaging (see com.bankdash.messaging.IdempotentConsumer).
CREATE TABLE processed_event (
    consumer     varchar(100) NOT NULL,
    event_id     uuid         NOT NULL,
    processed_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);
