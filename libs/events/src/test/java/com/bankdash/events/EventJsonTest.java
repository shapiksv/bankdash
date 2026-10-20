package com.bankdash.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The JSON on the wire is the contract between services, so its exact shape is pinned here. */
class EventJsonTest {

    final JsonMapper json = JsonMapper.builder().build();

    final UUID eventId = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    final EventEnvelope<UserRegistered> envelope = new EventEnvelope<>(eventId, UserRegistered.TYPE,
            UserRegistered.SCHEMA_VERSION, "auth-service", Instant.parse("2026-10-13T10:00:00Z"),
            new UserRegistered(userId, "jane@example.com", "Jane Doe"));

    @Test
    void userRegisteredHasTheDocumentedShape() {
        assertThat(json.writeValueAsString(envelope)).isEqualTo("""
                {"eventId":"00000000-0000-0000-0000-00000000000e","eventType":"UserRegistered","schemaVersion":1,\
                "source":"auth-service","occurredAt":"2026-10-13T10:00:00Z","payload":{"userId":"00000000-0000-0000-0000-000000000001",\
                "email":"jane@example.com","fullName":"Jane Doe"}}""");
    }

    @Test
    void roundTrips() {
        String text = json.writeValueAsString(envelope);

        assertThat(json.readValue(text, new TypeReference<EventEnvelope<UserRegistered>>() {})).isEqualTo(envelope);
    }

    @Test
    void dltTopicNames() {
        assertThat(Topics.dlt(Topics.AUTH_USER_REGISTERED)).isEqualTo("auth.user-registered.v1.DLT");
    }

    @Test
    void newEnvelopesGetAFreshId() {
        var a = EventEnvelope.of(UserRegistered.TYPE, 1, "test", Instant.EPOCH, "x");
        var b = EventEnvelope.of(UserRegistered.TYPE, 1, "test", Instant.EPOCH, "x");

        assertThat(a.eventId()).isNotEqualTo(b.eventId());
    }
}
