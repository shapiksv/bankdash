package com.bankdash.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Storage of the {@code outbox_event} table (see {@link Outbox} for its DDL). */
interface OutboxRepository {

    void insert(NewRow row);

    /**
     * The oldest {@code PENDING} rows whose key has no {@code FAILED} row, locked; rows locked by another relay
     * instance are skipped.
     */
    List<PendingRow> lockPending(int limit);

    void markPublished(long id, Instant publishedAt);

    /** Counts a failed send and keeps its error; {@code failed} turns the row {@code FAILED}. */
    void recordFailure(long id, String error, boolean failed);

    int deletePublishedBefore(Instant cutoff);

    record NewRow(UUID eventId, String topic, String key, String payload, Instant createdAt) {}

    /** @param attempts failed sends so far */
    record PendingRow(long id, UUID eventId, String topic, String key, String payload, int attempts) {}
}
