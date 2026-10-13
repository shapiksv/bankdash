package com.bankdash.messaging;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Turns a {@link ReceivedEvent}'s payload into its event record and validates it. */
public class EventPayloads {

    private final JsonMapper json;
    private final Validator validator;

    EventPayloads(JsonMapper json, Validator validator) {
        this.json = json;
        this.validator = validator;
    }

    /**
     * @throws InvalidEventException if the payload cannot be mapped to {@code type} or breaks its constraints;
     *     the record then goes to the DLT without retries
     */
    public <T> T read(ReceivedEvent event, Class<T> type) {
        T payload;
        try {
            payload = json.treeToValue(event.payload(), type);
        } catch (JacksonException e) {
            throw new InvalidEventException("Event " + event.eventId() + " has an unreadable " + type.getSimpleName(), e);
        }
        Set<ConstraintViolation<T>> violations = validator.validate(payload);
        if (!violations.isEmpty()) {
            throw new InvalidEventException("Event " + event.eventId() + " has an invalid " + type.getSimpleName() + ": "
                    + violations.stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .sorted()
                            .collect(Collectors.joining(", ")));
        }
        return payload;
    }
}
