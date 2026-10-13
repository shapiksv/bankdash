-- Transactional outbox of libs/messaging (see com.bankdash.messaging.Outbox). Signup writes its
-- auth.user-registered.v1 event here in the same transaction; the relay sends it to Kafka.
CREATE TABLE outbox_event (
    id           bigserial PRIMARY KEY,
    event_id     uuid         NOT NULL UNIQUE,
    topic        varchar(255) NOT NULL,
    message_key  varchar(255) NOT NULL,
    -- The whole event envelope, sent byte for byte (jsonb would reorder its keys).
    payload      json         NOT NULL,
    created_at   timestamptz  NOT NULL,
    published_at timestamptz
);

CREATE INDEX outbox_event_unpublished_idx ON outbox_event (id) WHERE published_at IS NULL;
CREATE INDEX outbox_event_published_at_idx ON outbox_event (published_at) WHERE published_at IS NOT NULL;
