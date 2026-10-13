package com.bankdash.messaging;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Runs an event handler at most once per event and consumer. Kafka delivers at least once (and the outbox may
 * send twice), so every listener goes through this.
 *
 * <p>The event id is recorded in {@code processed_event} in the same transaction as the handler's changes. If
 * the handler fails, both roll back and a redelivery processes the event again. Needs this table, created by the
 * service's own Flyway migration:
 *
 * <pre>{@code
 * CREATE TABLE processed_event (
 *     consumer     varchar(100) NOT NULL,
 *     event_id     uuid         NOT NULL,
 *     processed_at timestamptz  NOT NULL DEFAULT now(),
 *     PRIMARY KEY (consumer, event_id)
 * );
 * }</pre>
 */
public class IdempotentConsumer {

    private static final Logger log = LoggerFactory.getLogger(IdempotentConsumer.class);

    private final JdbcClient jdbc;
    private final TransactionOperations transactions;

    IdempotentConsumer(JdbcClient jdbc, TransactionOperations transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    /**
     * @param consumer a stable name per handler (e.g. {@code profile.user-registered}), so that two handlers in
     *     one service can each process the same event
     * @return false if this consumer had already processed {@code eventId} and the handler was skipped
     */
    public boolean process(String consumer, UUID eventId, Runnable handler) {
        Boolean processed = transactions.execute(status -> {
            int inserted = jdbc.sql("""
                            INSERT INTO processed_event (consumer, event_id) VALUES (?, ?)
                            ON CONFLICT DO NOTHING""")
                    .params(consumer, eventId)
                    .update();
            if (inserted == 0) {
                log.info("Skipping event {} for {}: already processed", eventId, consumer);
                return false;
            }
            handler.run();
            return true;
        });
        return Boolean.TRUE.equals(processed);
    }
}
