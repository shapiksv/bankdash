package com.bankdash.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

class JdbcOutboxRepository implements OutboxRepository {

    private final JdbcClient jdbc;

    JdbcOutboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(NewRow row) {
        jdbc.sql("""
                        INSERT INTO outbox_event (event_id, topic, message_key, payload, created_at)
                        VALUES (?, ?, ?, ?::json, ?)""")
                .params(row.eventId(), row.topic(), row.key(), row.payload(), Timestamp.from(row.createdAt()))
                .update();
    }

    @Override
    public List<PendingRow> lockPending(int limit) {
        return jdbc.sql("""
                        SELECT id, event_id, topic, message_key, payload::text AS payload
                        FROM outbox_event
                        WHERE published_at IS NULL
                        ORDER BY id
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED""")
                .param(limit)
                .query((rs, n) -> new PendingRow(rs.getLong("id"), rs.getObject("event_id", UUID.class),
                        rs.getString("topic"), rs.getString("message_key"), rs.getString("payload")))
                .list();
    }

    @Override
    public void markPublished(long id, Instant publishedAt) {
        jdbc.sql("UPDATE outbox_event SET published_at = ? WHERE id = ?")
                .params(Timestamp.from(publishedAt), id)
                .update();
    }

    @Override
    public int deletePublishedBefore(Instant cutoff) {
        return jdbc.sql("DELETE FROM outbox_event WHERE published_at < ?").param(Timestamp.from(cutoff)).update();
    }
}
