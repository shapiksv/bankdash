package com.bankdash.profile.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bankdash.events.Topics;
import com.bankdash.profile.ProfileIT;
import com.bankdash.profile.domain.Preferences;
import com.bankdash.testsupport.TopicReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.core.KafkaAdmin;

class UserRegisteredListenerIT extends ProfileIT {

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

    Map<String, Object> profileRow(UUID userId) {
        return jdbc.sql("SELECT * FROM user_profile WHERE user_id = ?").param(userId).query().singleRow();
    }

    @Test
    void createsTheProfileAndDefaultPreferences() {
        UUID userId = UUID.randomUUID();
        String local = "jane" + userId.toString().substring(0, 8);

        register(userId, local + "@example.com", "Jane Doe");

        Awaitility.await().atMost(WAIT).until(() -> hasProfile(userId));
        assertThat(profileRow(userId)).containsEntry("full_name", "Jane Doe").containsEntry("username", local);
        assertThat(jdbc.sql("SELECT * FROM preferences WHERE user_id = ?").param(userId).query().singleRow())
                .containsEntry("currency", "USD")
                .containsEntry("time_zone", "UTC")
                .containsEntry("notify_on_transfers", true)
                .containsEntry("notify_on_merchant_orders", false)
                .containsEntry("notify_on_recommendations", true);
    }

    @Test
    void aFailureInsideTheTransactionRollsEverythingBackAndTheRetryCreatesTheProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        CountDownLatch retryStarted = new CountDownLatch(1);
        CountDownLatch checked = new CountDownLatch(1);
        doAnswer(invocation -> {
            throw new TransientDataAccessResourceException("Connection reset");
        }).doAnswer(invocation -> {
            // Hold the retry open while the test looks at what the first attempt left behind.
            retryStarted.countDown();
            checked.await(WAIT.toSeconds(), TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(preferencesRepository).insertIfAbsent(forUser(userId));

        var event = register(userId, "user-" + userId + "@example.com", "Jane Doe");

        assertThat(retryStarted.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        // The first attempt had inserted both rows before it failed; they were rolled back.
        assertThat(hasProfile(userId)).isFalse();
        assertThat(processedRows(event.eventId())).isZero();
        checked.countDown();

        Awaitility.await().atMost(WAIT).until(() -> hasProfile(userId));
        assertThat(jdbc.sql("SELECT count(*) FROM preferences WHERE user_id = ?").param(userId)
                .query(Long.class).single()).isOne();
        assertThat(processedRows(event.eventId())).isOne();
        verify(preferencesRepository, times(2)).insertIfAbsent(forUser(userId));
    }

    static Preferences forUser(UUID userId) {
        return argThat(p -> p != null && userId.equals(p.userId()));
    }

    long processedRows(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM processed_event WHERE event_id = ?").param(eventId)
                .query(Long.class).single();
    }

    @Test
    void aDuplicateEventIsIgnoredAndDoesNotUndoEdits() {
        UUID userId = UUID.randomUUID();
        var event = register(userId, "user-" + userId + "@example.com", "Jane Doe");
        Awaitility.await().atMost(WAIT).until(() -> hasProfile(userId));
        jdbc.sql("UPDATE user_profile SET full_name = 'Jane Edited' WHERE user_id = ?").param(userId).update();

        send(userId.toString(), json.writeValueAsString(event));
        UUID marker = UUID.randomUUID();
        register(marker, "user-" + marker + "@example.com", "Marker");
        Awaitility.await().atMost(WAIT).until(() -> hasProfile(marker));

        assertThat(profileRow(userId)).containsEntry("full_name", "Jane Edited");
        assertThat(processedRows(event.eventId())).isOne();
    }

    @Test
    void aTakenDefaultUsernameGetsASuffix() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String email = "twin" + first.toString().substring(0, 8) + "@example.com";

        register(first, email, "First Twin");
        Awaitility.await().atMost(WAIT).until(() -> hasProfile(first));
        register(second, email.replace("@", "@other."), "Second Twin");
        Awaitility.await().atMost(WAIT).until(() -> hasProfile(second));

        String base = email.substring(0, email.indexOf('@'));
        assertThat(profileRow(first)).containsEntry("username", base);
        assertThat(profileRow(second)).containsEntry("username",
                base + "-" + second.toString().replace("-", "").substring(0, 6));
    }

    @Test
    void aPoisonMessageLandsInTheDltAndLaterEventsStillCreateProfiles() {
        String key = UUID.randomUUID().toString();

        send(key, "this is not an event");
        UUID next = registeredUser("After Poison");

        var dead = dlt.awaitOne(WAIT, r -> key.equals(r.key()));
        assertThat(dead.value()).isEqualTo("this is not an event");
        assertThat(new String(dead.headers().lastHeader("kafka_dlt-original-topic").value(), StandardCharsets.UTF_8))
                .isEqualTo(Topics.AUTH_USER_REGISTERED);
        assertThat(hasProfile(next)).isTrue();
    }

    @Test
    void anEventWithoutAUserIdLandsInTheDltWithoutCreatingAnything() {
        String key = UUID.randomUUID().toString();
        long before = jdbc.sql("SELECT count(*) FROM user_profile").query(Long.class).single();

        send(key, """
                {"eventId":"%s","eventType":"UserRegistered","occurredAt":"2026-10-13T10:00:00Z",\
                "payload":{"email":"jane@example.com","fullName":"Jane Doe"}}""".formatted(UUID.randomUUID()));

        var dead = dlt.awaitOne(WAIT, r -> key.equals(r.key()));
        assertThat(new String(dead.headers().lastHeader("kafka_dlt-exception-message").value(), StandardCharsets.UTF_8))
                .contains("userId");
        assertThat(jdbc.sql("SELECT count(*) FROM user_profile").query(Long.class).single()).isEqualTo(before);
    }
}
