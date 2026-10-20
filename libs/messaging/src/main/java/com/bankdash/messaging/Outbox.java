package com.bankdash.messaging;

import com.bankdash.events.EventEnvelope;
import java.time.Clock;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The only way business code emits a domain event. The event is written to the service's {@code outbox_event}
 * table in the caller's transaction, so it exists exactly when the business change commits; {@link OutboxRelay}
 * sends it to Kafka afterwards (at least once).
 *
 * <p>Needs this table, created by the service's own Flyway migration:
 *
 * <pre>{@code
 * CREATE TABLE outbox_event (
 *     id           bigserial PRIMARY KEY,   -- send order
 *     event_id     uuid         NOT NULL UNIQUE,
 *     topic        varchar(255) NOT NULL,
 *     message_key  varchar(255) NOT NULL,
 *     payload      json         NOT NULL,   -- the whole EventEnvelope, kept byte for byte (jsonb would reorder it)
 *     created_at   timestamptz  NOT NULL,
 *     status       varchar(16)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
 *     attempts     int          NOT NULL DEFAULT 0,   -- failed sends; see OutboxRelay for FAILED
 *     last_error   text,
 *     published_at timestamptz
 * );
 * CREATE INDEX outbox_event_pending_idx ON outbox_event (id) WHERE status = 'PENDING';
 * CREATE INDEX outbox_event_failed_key_idx ON outbox_event (message_key) WHERE status = 'FAILED';
 * CREATE INDEX outbox_event_published_at_idx ON outbox_event (published_at) WHERE status = 'PUBLISHED';
 * }</pre>
 */
public class Outbox {

    private final OutboxRepository repository;
    private final JsonMapper json;
    private final Clock clock;
    private final String source;

    /** @param source this service's name, sent as every envelope's {@code source} */
    Outbox(OutboxRepository repository, JsonMapper json, Clock clock, String source) {
        this.repository = repository;
        this.json = json;
        this.clock = clock;
        this.source = source;
    }

    /**
     * Records {@code payload} as an event of {@code eventType} for {@code topic}, keyed by {@code key}.
     *
     * @param eventType     the payload record's {@code TYPE}
     * @param schemaVersion the payload record's {@code SCHEMA_VERSION}
     *
     * @return the envelope that will be sent
     * @throws org.springframework.transaction.IllegalTransactionStateException without a transaction: an event
     *     written outside of the business transaction could be sent for a change that never commits
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> EventEnvelope<T> publish(String topic, String key, String eventType, int schemaVersion, T payload) {
        EventEnvelope<T> envelope = EventEnvelope.of(eventType, schemaVersion, source, clock.instant(), payload);
        repository.insert(new OutboxRepository.NewRow(
                envelope.eventId(), topic, key, json.writeValueAsString(envelope), envelope.occurredAt()));
        return envelope;
    }
}
