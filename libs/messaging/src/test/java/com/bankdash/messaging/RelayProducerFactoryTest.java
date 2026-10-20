package com.bankdash.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;

class RelayProducerFactoryTest {

    final DefaultKafkaProducerFactory<Object, Object> boot = new DefaultKafkaProducerFactory<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
            ProducerConfig.CLIENT_ID_CONFIG, "relay-test"));

    final ProducerFactory<String, String> relay =
            MessagingAutoConfiguration.relayProducerFactory(boot, Duration.ofSeconds(10));

    @Test
    void boundsEveryProducerTimeoutByTheSendTimeoutAndKeepsBootsSettings() {
        assertThat(relay.getConfigurationProperties())
                .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10_000L)
                .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10_000)
                .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000)
                .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    }

    /** KafkaProducer rejects delivery.timeout.ms < linger.ms + request.timeout.ms when it is created. */
    @Test
    void kafkaAcceptsTheSettings() {
        try (var producer = relay.createProducer()) {
            assertThat(producer).isNotNull();
        } finally {
            relay.reset();
        }
    }
}
