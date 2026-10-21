-- One customer per auth user, created from auth.user-registered.v1 together with a CHECKING and a SAVINGS account
-- and a first debit card. user_id is auth's user id (the JWT sub); there is no foreign key to auth, which owns its
-- own database.
CREATE TABLE customer (
    user_id    uuid PRIMARY KEY,
    -- The holder name of every card the customer adds later.
    full_name  varchar(100) NOT NULL,
    created_at timestamptz  NOT NULL
);

CREATE TABLE account (
    id         uuid PRIMARY KEY,
    user_id    uuid          NOT NULL REFERENCES customer (user_id),
    type       varchar(16)   NOT NULL CHECK (type IN ('CHECKING', 'SAVINGS')),
    -- Single currency (docs/architecture.md section 6).
    currency   varchar(3)    NOT NULL DEFAULT 'USD',
    balance    numeric(19,2) NOT NULL DEFAULT 0,
    created_at timestamptz   NOT NULL,
    -- Also keeps a replayed user-registered event from opening a second set of accounts.
    CONSTRAINT account_user_type_uk UNIQUE (user_id, type)
);

-- Card numbers are fake and never stored: only the last 4 digits are kept.
CREATE TABLE card (
    id           uuid PRIMARY KEY,
    -- Same as the account's user_id; kept here so ownership checks and the card limit read one table.
    user_id      uuid         NOT NULL,
    account_id   uuid         NOT NULL REFERENCES account (id),
    type         varchar(16)  NOT NULL CHECK (type IN ('DEBIT')),
    holder_name  varchar(100) NOT NULL,
    last4        char(4)      NOT NULL,
    expiry_month smallint     NOT NULL CHECK (expiry_month BETWEEN 1 AND 12),
    expiry_year  smallint     NOT NULL,
    -- BCrypt; null until the first PIN is set.
    pin_hash     varchar(100),
    -- EXPIRED is not stored: it is derived from the expiry when a card is read.
    status       varchar(16)  NOT NULL CHECK (status IN ('INACTIVE', 'ACTIVE', 'BLOCKED')),
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    CONSTRAINT card_pin_unless_inactive CHECK (status = 'INACTIVE' OR pin_hash IS NOT NULL)
);

-- A user's cards in the order GET /cards returns them.
CREATE INDEX card_user_idx ON card (user_id, created_at, id);

-- Transactional outbox of libs/messaging (see com.bankdash.messaging.Outbox). Blocking a card writes its
-- cards.status-changed.v1 event here in the same transaction; the relay sends it to Kafka.
CREATE TABLE outbox_event (
    id           bigserial PRIMARY KEY,
    event_id     uuid         NOT NULL UNIQUE,
    topic        varchar(255) NOT NULL,
    message_key  varchar(255) NOT NULL,
    -- The whole event envelope, sent byte for byte (jsonb would reorder its keys).
    payload      json         NOT NULL,
    created_at   timestamptz  NOT NULL,
    -- PENDING until sent; FAILED after max-attempts failed sends, which holds back later rows with the same key.
    status       varchar(16)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    attempts     int          NOT NULL DEFAULT 0,
    last_error   text,
    published_at timestamptz
);

CREATE INDEX outbox_event_pending_idx ON outbox_event (id) WHERE status = 'PENDING';
CREATE INDEX outbox_event_failed_key_idx ON outbox_event (message_key) WHERE status = 'FAILED';
CREATE INDEX outbox_event_published_at_idx ON outbox_event (published_at) WHERE status = 'PUBLISHED';

-- Idempotent consumers of libs/messaging (see com.bankdash.messaging.IdempotentConsumer).
CREATE TABLE processed_event (
    consumer     varchar(100) NOT NULL,
    event_id     uuid         NOT NULL,
    processed_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);
