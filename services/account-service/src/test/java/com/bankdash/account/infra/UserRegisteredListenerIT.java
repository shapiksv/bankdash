package com.bankdash.account.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bankdash.account.AccountIT;
import com.bankdash.account.domain.Card;
import com.bankdash.events.EventEnvelope;
import com.bankdash.events.Topics;
import com.bankdash.events.UserRegistered;
import com.bankdash.testsupport.TopicReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.core.KafkaAdmin;

class UserRegisteredListenerIT extends AccountIT {

    @Autowired
    KafkaAdmin kafkaAdmin;

    TopicReader dlt;

    @BeforeEach
    void subscribe() {
        dlt = new TopicReader(kafkaAdmin.getConfigurationProperties(), Topics.dlt(Topics.AUTH_USER_REGISTERED));
    }

    @AfterEach
    void close() {
        dlt.close();
    }

    List<Map<String, Object>> accounts(UUID userId) {
        return jdbc.sql("SELECT * FROM account WHERE user_id = ? ORDER BY type").param(userId).query().listOfRows();
    }

    List<Map<String, Object>> cards(UUID userId) {
        return jdbc.sql("SELECT * FROM card WHERE user_id = ?").param(userId).query().listOfRows();
    }

    long processedRows(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM processed_event WHERE event_id = ?").param(eventId)
                .query(Long.class).single();
    }

    /**
     * Sends another user-registered event for {@code userId} and waits until it was processed. It has the same key,
     * so it lands on the same partition: everything sent before it for that user has been processed too.
     */
    void sendMarkerAndAwait(UUID userId) {
        var marker = EventEnvelope.of(UserRegistered.TYPE, UserRegistered.SCHEMA_VERSION, "auth-service", Instant.now(),
                new UserRegistered(userId, "marker-" + userId + "@example.com", "Marker"));
        send(userId.toString(), json.writeValueAsString(marker));
        Awaitility.await().atMost(WAIT).until(() -> processedRows(marker.eventId()) == 1);
    }

    @Test
    void ac1_opensCheckingSavingsAndAnInactiveDebitCard() {
        UUID userId = registeredUser("Jane Doe");

        var accounts = accounts(userId);
        assertThat(accounts).extracting(a -> a.get("type")).containsExactly("CHECKING", "SAVINGS");
        assertThat(accounts).allSatisfy(a -> {
            assertThat(a.get("balance")).isEqualTo(new BigDecimal("0.00"));
            assertThat(a.get("currency")).isEqualTo("USD");
        });
        YearMonth expiry = YearMonth.now(ZoneOffset.UTC).plusYears(3);
        assertThat(cards(userId)).singleElement().satisfies(card -> assertThat(card)
                .containsEntry("account_id", accounts.getFirst().get("id"))
                .containsEntry("type", "DEBIT")
                .containsEntry("status", "INACTIVE")
                .containsEntry("pin_hash", null)
                .containsEntry("holder_name", "Jane Doe")
                .containsEntry("expiry_month", expiry.getMonthValue())
                .containsEntry("expiry_year", expiry.getYear())
                .hasEntrySatisfying("last4", last4 -> assertThat((String) last4).matches("[0-9]{4}")));
        assertThat(jdbc.sql("SELECT full_name FROM customer WHERE user_id = ?").param(userId).query(String.class).single())
                .isEqualTo("Jane Doe");
    }

    @Test
    void ac1_failureRollsBackAndTheRetryCreatesEverything() {
        UUID userId = UUID.randomUUID();
        doAnswer(invocation -> {
            throw new TransientDataAccessResourceException("Connection reset");
        }).doCallRealMethod().when(cardRepository).insert(forUser(userId));

        var event = register(userId, "Jane Doe");

        // Without a rollback of the first attempt, the retry would find the customer and open nothing.
        Awaitility.await().atMost(WAIT).until(() -> processedRows(event.eventId()) == 1);
        assertThat(accounts(userId)).hasSize(2);
        assertThat(cards(userId)).hasSize(1);
        verify(cardRepository, times(2)).insert(forUser(userId));
    }

    static Card forUser(UUID userId) {
        return argThat(card -> card != null && userId.equals(card.userId()));
    }

    @Test
    void ac2_duplicateEventIsIgnored() {
        UUID userId = UUID.randomUUID();
        var event = register(userId, "Jane Doe");
        Awaitility.await().atMost(WAIT).until(() -> hasCustomer(userId));

        send(userId.toString(), json.writeValueAsString(event));
        sendMarkerAndAwait(userId);

        assertThat(accounts(userId)).hasSize(2);
        assertThat(cards(userId)).hasSize(1);
        assertThat(processedRows(event.eventId())).isOne();
    }

    @Test
    void ac2_secondEventForTheSameUserCreatesNothing() {
        UUID userId = registeredUser("Jane Doe");

        // A replay of the registration with a new event id, e.g. from the DLT.
        sendMarkerAndAwait(userId);

        assertThat(accounts(userId)).hasSize(2);
        assertThat(cards(userId)).singleElement().satisfies(card -> assertThat(card).containsEntry("holder_name", "Jane Doe"));
    }

    @Test
    void poisonMessageLandsInTheDlt() {
        String key = UUID.randomUUID().toString();

        send(key, "this is not an event");
        UUID next = registeredUser("After Poison");

        var dead = dlt.awaitOne(WAIT, r -> key.equals(r.key()));
        assertThat(dead.value()).isEqualTo("this is not an event");
        assertThat(new String(dead.headers().lastHeader("kafka_dlt-original-topic").value(), StandardCharsets.UTF_8))
                .isEqualTo(Topics.AUTH_USER_REGISTERED);
        assertThat(cards(next)).hasSize(1);
    }

    @Test
    void eventWithoutUserIdLandsInTheDlt() {
        String key = UUID.randomUUID().toString();
        long before = jdbc.sql("SELECT count(*) FROM customer").query(Long.class).single();

        send(key, """
                {"eventId":"%s","eventType":"UserRegistered","schemaVersion":1,"source":"auth-service",\
                "occurredAt":"2026-10-21T10:00:00Z",\
                "payload":{"email":"jane@example.com","fullName":"Jane Doe"}}""".formatted(UUID.randomUUID()));

        var dead = dlt.awaitOne(WAIT, r -> key.equals(r.key()));
        assertThat(new String(dead.headers().lastHeader("kafka_dlt-exception-message").value(), StandardCharsets.UTF_8))
                .contains("userId");
        assertThat(jdbc.sql("SELECT count(*) FROM customer").query(Long.class).single()).isEqualTo(before);
    }
}
