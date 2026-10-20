package com.bankdash.events;

import java.time.Instant;
import java.util.UUID;

/**
 * What every Kafka record value looks like (JSON):
 * {@code {"eventId", "eventType", "schemaVersion", "source", "occurredAt", "payload"}}. The record key is set by the
 * producer (see {@link Topics}); consumers use {@code eventId} to drop duplicates.
 *
 * @param eventId       unique per event, also across redeliveries of the same event
 * @param eventType     the payload's {@code TYPE}, e.g. {@link UserRegistered#TYPE}
 * @param schemaVersion the payload's {@code SCHEMA_VERSION}, starting at 1. It goes up with a compatible change
 *                      (e.g. a new optional field); an incompatible one gets a new topic ({@code .v2})
 * @param source        the producing service's {@code spring.application.name}, e.g. {@code auth-service}
 * @param occurredAt    when the business change was committed
 * @param payload       the event record
 */
public record EventEnvelope<T>(
        UUID eventId, String eventType, int schemaVersion, String source, Instant occurredAt, T payload) {

    public static <T> EventEnvelope<T> of(
            String eventType, int schemaVersion, String source, Instant occurredAt, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, schemaVersion, source, occurredAt, payload);
    }
}
