-- The tables a service creates in its own migrations to use libs/messaging (see Outbox and IdempotentConsumer).
CREATE TABLE outbox_event (
    id           bigserial PRIMARY KEY,
    event_id     uuid         NOT NULL UNIQUE,
    topic        varchar(255) NOT NULL,
    message_key  varchar(255) NOT NULL,
    payload      json         NOT NULL,
    created_at   timestamptz  NOT NULL,
    status       varchar(16)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    attempts     int          NOT NULL DEFAULT 0,
    last_error   text,
    published_at timestamptz
);
CREATE INDEX outbox_event_pending_idx ON outbox_event (id) WHERE status = 'PENDING';
CREATE INDEX outbox_event_failed_key_idx ON outbox_event (message_key) WHERE status = 'FAILED';
CREATE INDEX outbox_event_published_at_idx ON outbox_event (published_at) WHERE status = 'PUBLISHED';
