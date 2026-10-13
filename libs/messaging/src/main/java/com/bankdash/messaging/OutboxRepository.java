package com.bankdash.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Storage of the {@code outbox_event} table (see {@link Outbox} for its DDL). */
interface OutboxRepository {

    void insert(NewRow row);

    /** The oldest unpublished rows, locked; rows locked by another relay instance are skipped. */
    List<PendingRow> lockPending(int limit);

    void markPublished(long id, Instant publishedAt);

    int deletePublishedBefore(Instant cutoff);

    record NewRow(UUID eventId, String topic, String key, String payload, Instant createdAt) {}

    record PendingRow(long id, UUID eventId, String topic, String key, String payload) {}
}
