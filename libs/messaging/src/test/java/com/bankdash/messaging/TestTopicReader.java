package com.bankdash.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/** Reads one topic from its current end, for asserting what was (or was not) sent. */
final class TestTopicReader implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;

    TestTopicReader(Map<String, Object> kafkaProperties, String topic) {
        Map<String, Object> config = new HashMap<>(kafkaProperties);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "test-reader-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumer = new KafkaConsumer<>(config);
        consumer.subscribe(List.of(topic));
        // Join the group and take the end offsets now, so that later records are seen.
        while (consumer.assignment().isEmpty()) {
            consumer.poll(Duration.ofMillis(100));
        }
        consumer.seekToEnd(consumer.assignment());
        consumer.assignment().forEach(consumer::position);
    }

    /** Every matching record that arrives within {@code timeout}. */
    List<ConsumerRecord<String, String>> readFor(Duration timeout, Predicate<ConsumerRecord<String, String>> filter) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        Instant end = Instant.now().plus(timeout);
        while (Instant.now().isBefore(end)) {
            consumer.poll(Duration.ofMillis(100)).forEach(record -> {
                if (filter.test(record)) {
                    matching.add(record);
                }
            });
        }
        return matching;
    }

    /** The first matching record, waiting at most {@code timeout}; fails if none arrives. */
    ConsumerRecord<String, String> awaitOne(Duration timeout, Predicate<ConsumerRecord<String, String>> filter) {
        Instant end = Instant.now().plus(timeout);
        while (Instant.now().isBefore(end)) {
            for (var record : consumer.poll(Duration.ofMillis(100))) {
                if (filter.test(record)) {
                    return record;
                }
            }
        }
        throw new AssertionError("No matching record within " + timeout);
    }

    @Override
    public void close() {
        consumer.close();
    }
}
