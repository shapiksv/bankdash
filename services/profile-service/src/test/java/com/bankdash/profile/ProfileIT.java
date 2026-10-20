package com.bankdash.profile;

import com.bankdash.events.EventEnvelope;
import com.bankdash.events.Topics;
import com.bankdash.events.UserRegistered;
import com.bankdash.profile.domain.PreferencesRepository;
import com.bankdash.testsupport.ContainersConfig;
import com.bankdash.testsupport.TestJwtIssuer;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base of profile-service's integration tests: real Postgres and Kafka, and {@link TestJwtIssuer} instead of
 * auth-service. Every subclass shares one Spring context, so the containers start once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
public abstract class ProfileIT {

    protected static final TestJwtIssuer issuer = TestJwtIssuer.start();
    protected static final Duration WAIT = Duration.ofSeconds(15);

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", issuer::jwksUri);
    }

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected KafkaTemplate<Object, Object> kafka;

    @Autowired
    protected JsonMapper json;

    /** The real repository unless a test stubs it. Declared here, so that all subclasses share one context. */
    @MockitoSpyBean
    protected PreferencesRepository preferencesRepository;

    protected String bearer(UUID userId) {
        return "Bearer " + issuer.token(userId);
    }

    /** Sends a user-registered event the way auth-service's outbox does. */
    protected EventEnvelope<UserRegistered> register(UUID userId, String email, String fullName) {
        var envelope = EventEnvelope.of(UserRegistered.TYPE, Instant.now(), new UserRegistered(userId, email, fullName));
        send(userId.toString(), json.writeValueAsString(envelope));
        return envelope;
    }

    protected void send(String key, String value) {
        try {
            kafka.send(Topics.AUTH_USER_REGISTERED, key, value).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected boolean hasProfile(UUID userId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM user_profile WHERE user_id = ?)").param(userId)
                .query(Boolean.class).single();
    }

    /** Registers a user and waits until the listener created their profile. */
    protected UUID registeredUser(String fullName) {
        UUID userId = UUID.randomUUID();
        register(userId, "user-" + userId + "@example.com", fullName);
        Awaitility.await().atMost(WAIT).until(() -> hasProfile(userId));
        return userId;
    }
}
