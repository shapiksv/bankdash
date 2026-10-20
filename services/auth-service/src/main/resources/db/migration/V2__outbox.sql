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
    -- PENDING until sent; FAILED after max-attempts failed sends, which holds back later rows with the same key.
    status       varchar(16)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    attempts     int          NOT NULL DEFAULT 0,
    last_error   text,
    published_at timestamptz
);

CREATE INDEX outbox_event_pending_idx ON outbox_event (id) WHERE status = 'PENDING';
CREATE INDEX outbox_event_failed_key_idx ON outbox_event (message_key) WHERE status = 'FAILED';
CREATE INDEX outbox_event_published_at_idx ON outbox_event (published_at) WHERE status = 'PUBLISHED';
