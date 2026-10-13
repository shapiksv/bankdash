package com.bankdash.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * An {@link com.bankdash.events.EventEnvelope} as a listener receives it: the envelope is parsed and validated by
 * the deserializer, the payload is still JSON. Convert it with {@link EventPayloads#read}.
 *
 * <pre>{@code
 * @KafkaListener(topics = Topics.AUTH_USER_REGISTERED)
 * void on(ReceivedEvent event) {
 *     UserRegistered payload = payloads.read(event, UserRegistered.class);
 *     idempotentConsumer.process("profile.user-registered", event.eventId(), () -> ...);
 * }
 * }</pre>
 */
public record ReceivedEvent(
        @NotNull UUID eventId, @NotBlank String eventType, @NotNull Instant occurredAt, @NotNull JsonNode payload) {}
