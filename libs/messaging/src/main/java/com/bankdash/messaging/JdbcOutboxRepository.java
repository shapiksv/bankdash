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
                        SELECT id, event_id, topic, message_key, payload::text AS payload, attempts
                        FROM outbox_event e
                        WHERE status = 'PENDING'
                          AND NOT EXISTS (SELECT 1 FROM outbox_event f
                                          WHERE f.status = 'FAILED' AND f.message_key = e.message_key)
                        ORDER BY id
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED""")
                .param(limit)
                .query((rs, n) -> new PendingRow(rs.getLong("id"), rs.getObject("event_id", UUID.class),
                        rs.getString("topic"), rs.getString("message_key"), rs.getString("payload"),
                        rs.getInt("attempts")))
                .list();
    }

    @Override
    public void markPublished(long id, Instant publishedAt) {
        jdbc.sql("UPDATE outbox_event SET status = 'PUBLISHED', published_at = ? WHERE id = ?")
                .params(Timestamp.from(publishedAt), id)
                .update();
    }

    @Override
    public void recordFailure(long id, String error, boolean failed) {
        jdbc.sql("""
                        UPDATE outbox_event SET attempts = attempts + 1, last_error = ?, status = ?
                        WHERE id = ?""")
                .params(error, failed ? "FAILED" : "PENDING", id)
                .update();
    }

    @Override
    public int deletePublishedBefore(Instant cutoff) {
        return jdbc.sql("DELETE FROM outbox_event WHERE status = 'PUBLISHED' AND published_at < ?")
                .param(Timestamp.from(cutoff))
                .update();
    }
}
