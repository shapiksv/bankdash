package com.bankdash.messaging;

import com.bankdash.messaging.OutboxRepository.PendingRow;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Sends committed outbox rows to Kafka, oldest first, and marks them as published.
 *
 * <p>Delivery is at least once: a crash between the broker's ack and the commit of {@code published_at} sends
 * the row again on the next run, and consumers drop the duplicate by {@code eventId}. A failed send ends the run
 * so that no later row overtakes it; the next run starts again from the failed row.
 *
 * <p>The relay's producer gives a record up after {@code sendTimeout} (see
 * {@link MessagingAutoConfiguration#relayProducerFactory}), so a send ends within that time with the producer's
 * own result. The relay waits a little longer than that only as a safety net.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    /** Beyond {@code sendTimeout}, so that the producer, not this wait, decides whether a record failed. */
    static final Duration SEND_GRACE = Duration.ofMillis(500);

    private final OutboxRepository repository;
    private final KafkaOperations<String, String> kafka;
    private final TransactionOperations transactions;
    private final MessagingProperties.Outbox properties;
    private final Clock clock;

    OutboxRelay(OutboxRepository repository, KafkaOperations<String, String> kafka, TransactionOperations transactions,
            MessagingProperties.Outbox properties, Clock clock) {
        this.repository = repository;
        this.kafka = kafka;
        this.transactions = transactions;
        this.properties = properties;
        this.clock = clock;
    }

    /** One run: sends up to {@code batchSize} rows. Returns how many were published. */
    public int publishPending() {
        Integer published = transactions.execute(status -> {
            int sent = 0;
            for (PendingRow row : repository.lockPending(properties.batchSize())) {
                if (!send(row)) {
                    break;
                }
                repository.markPublished(row.id(), clock.instant());
                sent++;
            }
            return sent;
        });
        return published == null ? 0 : published;
    }

    /** Deletes rows that were published longer ago than the retention. */
    public int purgePublished() {
        Integer deleted = transactions.execute(
                status -> repository.deletePublishedBefore(clock.instant().minus(properties.retention())));
        return deleted == null ? 0 : deleted;
    }

    private boolean send(PendingRow row) {
        try {
            kafka.send(row.topic(), row.key(), row.payload())
                    .get(properties.sendTimeout().plus(SEND_GRACE).toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warn("Could not send outbox event {} to {}; retrying on the next run", row.eventId(), row.topic(), e);
            return false;
        }
    }
}
