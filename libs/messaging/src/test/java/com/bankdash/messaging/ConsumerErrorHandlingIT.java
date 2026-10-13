package com.bankdash.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.events.Topics;
import com.bankdash.testsupport.ContainersConfig;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * The DefaultErrorHandler setup: idempotent processing, retries for transient errors, and the DLT for records
 * that can never succeed.
 */
@SpringBootTest
@Import(ContainersConfig.class)
class ConsumerErrorHandlingIT {

    static final String TOPIC = Topics.AUTH_USER_REGISTERED;
    static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    KafkaTemplate<Object, Object> kafka;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Autowired
    RecordingListener listener;

    @Autowired
    JdbcClient jdbc;

    TestTopicReader dlt;
    final String key = UUID.randomUUID().toString();

    @BeforeEach
    void subscribe() {
        dlt = new TestTopicReader(kafkaAdmin.getConfigurationProperties(), Topics.dlt(TOPIC));
    }

    @AfterEach
    void close() {
        dlt.close();
    }

    static String envelope(UUID eventId, String fullName) {
        return """
                {"eventId":"%s","eventType":"UserRegistered","occurredAt":"2026-10-13T10:00:00Z",\
                "payload":{"userId":"%s","email":"jane@example.com","fullName":"%s"}}"""
                .formatted(eventId, UUID.randomUUID(), fullName);
    }

    void send(String value) throws Exception {
        kafka.send(TOPIC, key, value).get();
    }

    static String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @Test
    void aValidEventIsHandledOnce() throws Exception {
        UUID eventId = UUID.randomUUID();

        send(envelope(eventId, "Jane Doe"));

        Awaitility.await().atMost(WAIT).until(() -> listener.handled.contains(eventId));
    }

    @Test
    void aRedeliveredEventIsSkipped() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID marker = UUID.randomUUID();

        send(envelope(eventId, "Jane Doe"));
        send(envelope(eventId, "Jane Doe"));
        send(envelope(marker, "Jane Doe"));

        // Same key, same partition: once the marker is handled, both copies were seen.
        Awaitility.await().atMost(WAIT).until(() -> listener.handled.contains(marker));
        assertThat(listener.attempts(eventId)).isEqualTo(2);
        assertThat(listener.handled).containsOnlyOnce(eventId);
        assertThat(jdbc.sql("SELECT count(*) FROM processed_event WHERE event_id = ?").param(eventId)
                .query(Long.class).single()).isOne();
    }

    @Test
    void notJsonGoesToTheDltUntouchedAndTheNextRecordStillGetsThrough() throws Exception {
        UUID next = UUID.randomUUID();

        send("not-json");
        send(envelope(next, "Jane Doe"));

        var dead = dlt.awaitOne(WAIT, r -> key.equals(r.key()));
        assertThat(dead.value()).isEqualTo("not-json");
        assertThat(header(dead, "kafka_dlt-original-topic")).isEqualTo(TOPIC);
        assertThat(header(dead, "kafka_dlt-exception-fqcn"))
                .isEqualTo("org.springframework.kafka.support.serializer.DeserializationException");
        Awaitility.await().atMost(WAIT).until(() -> listener.handled.contains(next));
    }

    @Test
    void anEnvelopeWithoutItsIdGoesToTheDlt() throws Exception {
        send("""
                {"eventType":"UserRegistered","occurredAt":"2026-10-13T10:00:00Z","payload":{}}""");

        var dead = dlt.awaitOne(WAIT, r -> key.equals(r.key()));
        assertThat(header(dead, "kafka_dlt-exception-fqcn"))
                .isEqualTo("org.springframework.kafka.support.serializer.DeserializationException");
    }

    @Test
    void anInvalidPayloadGoesToTheDltWithoutRetries() throws Exception {
        UUID eventId = UUID.randomUUID();

        send("""
                {"eventId":"%s","eventType":"UserRegistered","occurredAt":"2026-10-13T10:00:00Z",\
                "payload":{"email":"jane@example.com"}}""".formatted(eventId));

        var dead = dlt.awaitOne(WAIT, r -> key.equals(r.key()));
        assertThat(header(dead, "kafka_dlt-exception-cause-fqcn")).isEqualTo(InvalidEventException.class.getName());
        assertThat(header(dead, "kafka_dlt-exception-message")).contains("fullName", "userId");
        assertThat(dead.value()).contains(eventId.toString());
        assertThat(listener.attempts(eventId)).isZero();
    }

    @Test
    void aNonRetryableErrorGoesToTheDltAfterOneAttempt() throws Exception {
        UUID eventId = UUID.randomUUID();

        send(envelope(eventId, "illegal-argument"));

        var dead = dlt.awaitOne(WAIT, r -> key.equals(r.key()));
        assertThat(header(dead, "kafka_dlt-exception-cause-fqcn")).isEqualTo(IllegalArgumentException.class.getName());
        assertThat(listener.attempts(eventId)).isOne();
    }

    @Test
    void aTransientErrorIsRetriedThreeTimesThenGoesToTheDlt() throws Exception {
        UUID eventId = UUID.randomUUID();

        send(envelope(eventId, "always-fails"));

        var dead = dlt.awaitOne(WAIT, r -> key.equals(r.key()));
        assertThat(header(dead, "kafka_dlt-exception-cause-fqcn")).isEqualTo(IllegalStateException.class.getName());
        assertThat(listener.attempts(eventId)).isEqualTo(4);
        assertThat(dead.value()).contains(eventId.toString(), "always-fails");
    }
}
