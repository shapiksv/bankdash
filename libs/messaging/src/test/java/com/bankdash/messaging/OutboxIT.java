package com.bankdash.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.bankdash.events.Topics;
import com.bankdash.events.UserRegistered;
import com.bankdash.testsupport.ContainersConfig;
import com.bankdash.testsupport.TopicReader;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.KafkaException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** The outbox against real Postgres and Kafka, with the relay running every 100 ms. */
@SpringBootTest
@Import(ContainersConfig.class)
class OutboxIT {

    static final String TOPIC = Topics.AUTH_USER_REGISTERED;

    @Autowired
    Outbox outbox;

    @Autowired
    OutboxRelay relay;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Autowired
    KafkaTemplate<?, ?> kafka;

    @Autowired
    OutboxRelayScheduler scheduler;

    @Autowired
    MessagingProperties properties;

    TopicReader topic;
    final String key = UUID.randomUUID().toString();

    @BeforeEach
    void subscribe() {
        topic = new TopicReader(kafkaAdmin.getConfigurationProperties(), TOPIC);
    }

    @AfterEach
    void close() {
        topic.close();
    }

    UserRegistered payload() {
        return new UserRegistered(UUID.fromString(key), "jane@example.com", "Jane Doe");
    }

    @Test
    void aCommittedEventIsSentWithItsKeyAndEnvelopeAndMarkedPublished() {
        var envelope = transactions.execute(status -> outbox.publish(TOPIC, key, UserRegistered.TYPE, payload()));

        var record = topic.awaitOne(Duration.ofSeconds(10), r -> key.equals(r.key()));

        assertThat(record.value())
                .contains("\"eventId\":\"" + envelope.eventId() + "\"")
                .contains("\"eventType\":\"UserRegistered\"")
                .contains("\"payload\":{\"userId\":\"" + key + "\",\"email\":\"jane@example.com\",\"fullName\":\"Jane Doe\"}");
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> publishedAt(envelope.eventId()) != null);
    }

    @Test
    void nothingIsSentBeforeTheTransactionCommits() {
        transactions.executeWithoutResult(status -> {
            outbox.publish(TOPIC, key, UserRegistered.TYPE, payload());

            // The relay keeps polling meanwhile; the row is invisible to it until the commit.
            assertThat(topic.readFor(Duration.ofMillis(1500), r -> key.equals(r.key()))).isEmpty();
        });

        assertThat(topic.awaitOne(Duration.ofSeconds(10), r -> key.equals(r.key()))).isNotNull();
    }

    @Test
    void aRolledBackTransactionNeverSends() {
        transactions.executeWithoutResult(status -> {
            outbox.publish(TOPIC, key, UserRegistered.TYPE, payload());
            status.setRollbackOnly();
        });

        assertThat(topic.readFor(Duration.ofMillis(1500), r -> key.equals(r.key()))).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_event WHERE message_key = ?").param(key)
                .query(Long.class).single()).isZero();
    }

    @Test
    void publishingOutsideATransactionIsRefused() {
        assertThatExceptionOfType(IllegalTransactionStateException.class)
                .isThrownBy(() -> outbox.publish(TOPIC, key, UserRegistered.TYPE, payload()));
    }

    @Test
    void eventsWithTheSameKeyArriveInOrder() {
        for (int i = 0; i < 5; i++) {
            String name = "Jane " + i;
            transactions.executeWithoutResult(status -> outbox.publish(TOPIC, key, UserRegistered.TYPE,
                    new UserRegistered(UUID.fromString(key), "jane@example.com", name)));
        }

        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> unpublished(key) == 0);
        assertThat(sentNames()).containsExactly("Jane 0", "Jane 1", "Jane 2", "Jane 3", "Jane 4");
    }

    @Test
    void aSendFailingInTheMiddleOfABatchStopsTheRunAndTheNextRunKeepsTheOrder() {
        // This test drives its own relay, so the scheduled one must not take the rows.
        scheduler.stop();
        try {
            for (int i = 0; i < 5; i++) {
                String name = "Jane " + i;
                transactions.executeWithoutResult(status -> outbox.publish(TOPIC, key, UserRegistered.TYPE,
                        new UserRegistered(UUID.fromString(key), "jane@example.com", name)));
            }
            // A no-op update moves "Jane 0" to the end of the heap, and ANALYZE makes the planner scan the (small)
            // heap instead of the id index, so only ORDER BY id still reads "Jane 0" first.
            jdbc.sql("UPDATE outbox_event SET created_at = created_at WHERE message_key = ? AND payload::text LIKE ?")
                    .params(key, "%\"Jane 0\"%").update();
            jdbc.sql("ANALYZE outbox_event").update();
            OutboxRelay relay = new OutboxRelay(new JdbcOutboxRepository(jdbc), failingOnce("\"Jane 1\""),
                    transactions, properties.outbox(), Clock.systemUTC());

            relay.publishPending();

            assertThat(publishedNames()).containsExactly("Jane 0");
            List<String> sent = new ArrayList<>(sentNames());
            assertThat(sent).containsExactly("Jane 0");

            relay.publishPending();

            assertThat(unpublished(key)).isZero();
            sent.addAll(sentNames());
            assertThat(sent).containsExactly("Jane 0", "Jane 1", "Jane 2", "Jane 3", "Jane 4");
        } finally {
            scheduler.start();
        }
    }

    /** The real template, except that the first send of the record containing {@code marker} fails. */
    @SuppressWarnings("unchecked")
    KafkaOperations<String, String> failingOnce(String marker) {
        KafkaOperations<String, String> real = (KafkaOperations<String, String>) kafka;
        KafkaOperations<String, String> failing = mock(KafkaOperations.class, delegatesTo(real));
        doReturn(CompletableFuture.failedFuture(new KafkaException("Broker unavailable")))
                .doAnswer(delegatesTo(real))
                .when(failing).send(eq(TOPIC), eq(key), contains(marker));
        return failing;
    }

    List<String> publishedNames() {
        return jdbc.sql("""
                        SELECT payload->'payload'->>'fullName' FROM outbox_event
                        WHERE message_key = ? AND published_at IS NOT NULL
                        ORDER BY id""")
                .param(key).query(String.class).list();
    }

    List<String> sentNames() {
        return topic.readFor(Duration.ofSeconds(1), r -> key.equals(r.key())).stream()
                .map(r -> r.value().replaceAll(".*\"fullName\":\"([^\"]+)\".*", "$1"))
                .toList();
    }

    @Test
    void theRelaySendsWithItsOwnProducerTimeouts() {
        var template = (KafkaTemplate<?, ?>) ReflectionTestUtils.getField(relay, "kafka");

        long sendTimeout = properties.outbox().sendTimeout().toMillis();
        assertThat(template.getProducerFactory().getConfigurationProperties())
                .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, sendTimeout)
                .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (int) sendTimeout)
                .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) sendTimeout / 2);
    }

    @Test
    void publishedRowsArePurgedAfterTheRetention() {
        var envelope = transactions.execute(status -> outbox.publish(TOPIC, key, UserRegistered.TYPE, payload()));
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> publishedAt(envelope.eventId()) != null);
        jdbc.sql("UPDATE outbox_event SET published_at = now() - interval '8 days' WHERE event_id = ?")
                .param(envelope.eventId()).update();

        relay.purgePublished();

        assertThat(jdbc.sql("SELECT count(*) FROM outbox_event WHERE event_id = ?").param(envelope.eventId())
                .query(Long.class).single()).isZero();
    }

    Timestamp publishedAt(UUID eventId) {
        return jdbc.sql("SELECT published_at FROM outbox_event WHERE event_id = ?").param(eventId)
                .query(Timestamp.class).optional().orElse(null);
    }

    long unpublished(String key) {
        return jdbc.sql("SELECT count(*) FROM outbox_event WHERE message_key = ? AND published_at IS NULL").param(key)
                .query(Long.class).single();
    }
}
