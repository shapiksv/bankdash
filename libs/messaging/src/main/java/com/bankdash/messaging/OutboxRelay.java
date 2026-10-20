package com.bankdash.messaging;

import com.bankdash.messaging.OutboxRepository.PendingRow;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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
 * <p>Every failed send is counted in the row's {@code attempts} and {@code last_error}. After {@code maxAttempts}
 * the row becomes {@code FAILED}, which is logged at ERROR, and the run goes on with the next row. To keep the order
 * per message key, a {@code FAILED} row holds back every later row with the same key (they stay {@code PENDING});
 * rows with other keys keep flowing. Once the cause is fixed, setting the row back to {@code PENDING} (with
 * {@code attempts = 0}) sends it and then the rows behind it, in order.
 *
 * <p>A send that times out cannot tell a record that can never be sent (a missing topic, since auto-creation is
 * off) from a broker that is down. So a broker outage longer than about {@code maxAttempts × sendTimeout} also
 * marks the oldest row as {@code FAILED}; it then needs the manual reset above.
 *
 * <p>The failure is recorded in the run's own transaction, not in a separate one: the run holds the row's lock,
 * so a second transaction updating the row would wait for the run forever. A failed send does not roll the run
 * back, so the count survives; only a database error would, and then nothing of the run is kept.
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
            // Keys whose row failed for good during this run; lockPending already leaves out older ones.
            Set<String> heldBack = new HashSet<>();
            for (PendingRow row : repository.lockPending(properties.batchSize())) {
                if (heldBack.contains(row.key())) {
                    continue;
                }
                Exception failure = send(row);
                if (failure == null) {
                    repository.markPublished(row.id(), clock.instant());
                    sent++;
                    continue;
                }
                if (failure instanceof InterruptedException || !giveUp(row, failure)) {
                    break;
                }
                heldBack.add(row.key());
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

    /** @return null if the broker acknowledged the record, otherwise why it was not sent */
    private Exception send(PendingRow row) {
        try {
            kafka.send(row.topic(), row.key(), row.payload())
                    .get(properties.sendTimeout().plus(SEND_GRACE).toMillis(), TimeUnit.MILLISECONDS);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        } catch (ExecutionException e) {
            return e.getCause() instanceof Exception cause ? cause : e;
        } catch (Exception e) {
            return e;
        }
    }

    /** Records the failed attempt; returns whether it was the last one, so that the row is now FAILED. */
    private boolean giveUp(PendingRow row, Exception failure) {
        int attempts = row.attempts() + 1;
        boolean last = attempts >= properties.maxAttempts();
        repository.recordFailure(row.id(), describe(failure), last);
        if (last) {
            log.error("Gave up on outbox event {} to {} after {} attempts; later events with key {} wait until it is"
                    + " set back to PENDING", row.eventId(), row.topic(), attempts, row.key(), failure);
        } else {
            log.warn("Could not send outbox event {} to {} (attempt {} of {}); retrying on the next run",
                    row.eventId(), row.topic(), attempts, properties.maxAttempts(), failure);
        }
        return last;
    }

    /**
     * The error with its causes: KafkaTemplate wraps the producer's error in "Send failed", and the producer's
     * own errors name the topic in one cause and the broker's answer in the next.
     */
    static String describe(Throwable failure) {
        StringJoiner text = new StringJoiner("; caused by ");
        for (Throwable t = failure; t != null && text.length() < 2000; t = t.getCause()) {
            text.add(t.toString());
        }
        return text.toString();
    }
}
