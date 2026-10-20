package com.bankdash.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.bankdash.auth.domain.RefreshTokenRepository;
import com.bankdash.events.Topics;
import com.bankdash.testsupport.ContainersConfig;
import com.bankdash.testsupport.TopicReader;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/** Signup emits auth.user-registered.v1 through the outbox, and only when the user is actually created. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class SignupEventIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Autowired
    JsonMapper json;

    @MockitoSpyBean
    RefreshTokenRepository refreshTokens;

    TopicReader topic;
    final String email = "user-" + UUID.randomUUID() + "@example.com";

    @BeforeEach
    void subscribe() {
        topic = new TopicReader(kafkaAdmin.getConfigurationProperties(), Topics.AUTH_USER_REGISTERED);
    }

    @AfterEach
    void close() {
        topic.close();
    }

    MvcTestResult signup(String email) {
        return mvc.post().uri("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "  Jane Doe ", "email": "%s", "password": "secret123"}""".formatted(email))
                .exchange();
    }

    UUID userId() {
        return jdbc.sql("SELECT id FROM user_credential WHERE email = ?").param(email).query(UUID.class).single();
    }

    @Test
    void signupPublishesUserRegisteredKeyedByTheUserId() {
        assertThat(signup(email.toUpperCase())).hasStatus(HttpStatus.CREATED);
        String userId = userId().toString();

        var record = topic.awaitOne(Duration.ofSeconds(15), r -> userId.equals(r.key()));

        var envelope = json.readTree(record.value());
        assertThat(envelope.get("eventId").asString()).isNotBlank();
        assertThat(envelope.get("eventType").asString()).isEqualTo("UserRegistered");
        assertThat(envelope.get("occurredAt").asString()).isNotBlank();
        var payload = envelope.get("payload");
        assertThat(payload.get("userId").asString()).isEqualTo(userId);
        assertThat(payload.get("email").asString()).isEqualTo(email);
        assertThat(payload.get("fullName").asString()).isEqualTo("Jane Doe");
    }

    @Test
    void aDuplicateEmailEmitsNoSecondEvent() {
        signup(email);
        String userId = userId().toString();
        topic.awaitOne(Duration.ofSeconds(15), r -> userId.equals(r.key()));

        assertThat(signup(email)).hasStatus(HttpStatus.CONFLICT);

        // The failed signup rolled back, so its outbox row never existed.
        assertThat(outboxRows()).isOne();
        assertThat(topic.readFor(Duration.ofSeconds(2), r -> r.value().contains(email))).isEmpty();
    }

    /** The session is started after the event is written, so this fails the transaction after the outbox write. */
    @Test
    void aSignupThatFailsAfterWritingTheEventLeavesNeitherTheUserNorTheEvent() {
        AtomicLong rowsBeforeFailure = new AtomicLong(-1);
        doAnswer(invocation -> {
            // Same thread, same transaction: the uncommitted outbox row is visible here.
            rowsBeforeFailure.set(outboxRows());
            throw new IllegalStateException("Refresh token store unavailable");
        }).when(refreshTokens).insert(any());

        // MockMvc has no error page; a running server answers 500.
        assertThat(signup(email)).failure().rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Refresh token store unavailable");

        assertThat(rowsBeforeFailure).hasValue(1);
        assertThat(outboxRows()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM user_credential WHERE email = ?").param(email)
                .query(Long.class).single()).isZero();
        assertThat(topic.readFor(Duration.ofSeconds(2), r -> r.value().contains(email))).isEmpty();
    }

    long outboxRows() {
        return jdbc.sql("SELECT count(*) FROM outbox_event WHERE payload->'payload'->>'email' = ?").param(email)
                .query(Long.class).single();
    }
}
