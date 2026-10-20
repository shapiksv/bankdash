package com.bankdash.messaging;

import com.bankdash.events.Topics;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires Kafka the same way in every service that depends on libs/messaging: the outbox (opt-in), JSON envelopes
 * for listeners, idempotent consumers, and retries followed by a dead letter topic.
 */
@AutoConfiguration(afterName = {
    "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
    "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
    "org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration",
})
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingAutoConfiguration {

    /**
     * Listeners receive a {@link ReceivedEvent}. A value that is not JSON, or an envelope without its required
     * fields, fails in the {@link ErrorHandlingDeserializer}; the error handler sends it to the DLT untouched.
     */
    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    DefaultKafkaConsumerFactoryCustomizer receivedEventDeserializer(JsonMapper json, Validator validator) {
        SpringValidatorAdapter springValidator = new SpringValidatorAdapter(validator);
        return factory -> {
            DefaultKafkaConsumerFactory raw = factory;
            raw.setKeyDeserializerSupplier(StringDeserializer::new);
            raw.setValueDeserializerSupplier(() -> {
                var deserializer = new ErrorHandlingDeserializer<>(
                        new JacksonJsonDeserializer<>(ReceivedEvent.class, json, false));
                deserializer.setValidator(springValidator);
                return deserializer;
            });
        };
    }

    @Bean
    EventPayloads eventPayloads(JsonMapper json, Validator validator) {
        return new EventPayloads(json, validator);
    }

    @Bean
    IdempotentConsumer idempotentConsumer(JdbcClient jdbc, PlatformTransactionManager transactionManager) {
        return new IdempotentConsumer(jdbc, new TransactionTemplate(transactionManager));
    }

    /**
     * Picked up by Boot's listener container factory. Transient failures (database down, lock timeout) are
     * retried {@code retries} times; then, or at once for records that can never succeed, the record goes to
     * {@code <topic>.DLT} with the original headers plus exception headers. Kafka picks the DLT partition.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(ProducerFactory<?, ?> producerFactory, MessagingProperties properties) {
        KafkaTemplate<Object, Object> deadLetters = new KafkaTemplate<>(deadLetterProducerFactory(producerFactory));
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetters,
                (record, exception) -> new TopicPartition(Topics.dlt(record.topic()), -1));
        MessagingProperties.Consumer consumer = properties.consumer();
        DefaultErrorHandler handler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(consumer.retryInterval().toMillis(), consumer.retries()));
        handler.addNotRetryableExceptions(
                InvalidEventException.class, ValidationException.class, IllegalArgumentException.class);
        return handler;
    }

    @SuppressWarnings("unchecked")
    private static ProducerFactory<Object, Object> deadLetterProducerFactory(ProducerFactory<?, ?> producerFactory) {
        return (ProducerFactory<Object, Object>) producerFactory.copyWithConfigurationOverride(
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, DeadLetterValueSerializer.class));
    }

    /** Only for services that produce events: they opt in and own an {@code outbox_event} table. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "bankdash.messaging.outbox.enabled", havingValue = "true")
    static class OutboxConfiguration {

        @Bean
        Outbox outbox(JdbcClient jdbc, JsonMapper json, ObjectProvider<Clock> clock) {
            return new Outbox(new JdbcOutboxRepository(jdbc), json, clock.getIfAvailable(Clock::systemUTC));
        }

        @Bean
        OutboxRelay outboxRelay(JdbcClient jdbc, ProducerFactory<?, ?> producerFactory,
                PlatformTransactionManager transactionManager, MessagingProperties properties, ObjectProvider<Clock> clock) {
            KafkaTemplate<String, String> kafka =
                    new KafkaTemplate<>(relayProducerFactory(producerFactory, properties.outbox().sendTimeout()));
            return new OutboxRelay(new JdbcOutboxRepository(jdbc), kafka,
                    new TransactionTemplate(transactionManager), properties.outbox(), clock.getIfAvailable(Clock::systemUTC));
        }

        @Bean
        OutboxRelayScheduler outboxRelayScheduler(OutboxRelay relay, MessagingProperties properties) {
            return new OutboxRelayScheduler(relay, properties.outbox().pollInterval());
        }
    }

    /**
     * Boot's producer settings, with String serializers (the outbox row already holds the JSON, so it is sent byte
     * for byte) and every producer timeout bounded by {@code sendTimeout}: {@code send} blocks for metadata at most
     * that long, and the producer gives a record up after it instead of delivering it later, when the relay has
     * already counted it as failed and would send it again.
     */
    @SuppressWarnings("unchecked")
    static ProducerFactory<String, String> relayProducerFactory(ProducerFactory<?, ?> producerFactory, Duration sendTimeout) {
        int timeoutMs = Math.toIntExact(sendTimeout.toMillis());
        return (ProducerFactory<String, String>) producerFactory.copyWithConfigurationOverride(Map.of(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, (long) timeoutMs,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, timeoutMs,
                // Kafka requires delivery.timeout.ms >= linger.ms + request.timeout.ms.
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutMs / 2));
    }
}
