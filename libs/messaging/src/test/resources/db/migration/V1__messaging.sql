-- The tables a service creates in its own migrations to use libs/messaging (see Outbox and IdempotentConsumer).
CREATE TABLE outbox_event (
    id           bigserial PRIMARY KEY,
    event_id     uuid         NOT NULL UNIQUE,
    topic        varchar(255) NOT NULL,
    message_key  varchar(255) NOT NULL,
    payload      json         NOT NULL,
    created_at   timestamptz  NOT NULL,
    published_at timestamptz
);
CREATE INDEX outbox_event_unpublished_idx ON outbox_event (id) WHERE published_at IS NULL;
CREATE INDEX outbox_event_published_at_idx ON outbox_event (published_at) WHERE published_at IS NOT NULL;
