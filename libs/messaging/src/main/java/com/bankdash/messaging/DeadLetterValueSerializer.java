package com.bankdash.messaging;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.serialization.Serializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Value serializer of the DLT producer. A record that failed deserialization arrives as its original bytes and is
 * written unchanged; a record that failed in the listener is a {@link ReceivedEvent} and is written as JSON again.
 */
public class DeadLetterValueSerializer implements Serializer<Object> {

    private final JsonMapper json = JsonMapper.builder().build();

    @Override
    public byte[] serialize(String topic, Object data) {
        return switch (data) {
            case null -> null;
            case byte[] bytes -> bytes;
            case String text -> text.getBytes(StandardCharsets.UTF_8);
            default -> json.writeValueAsBytes(data);
        };
    }
}
