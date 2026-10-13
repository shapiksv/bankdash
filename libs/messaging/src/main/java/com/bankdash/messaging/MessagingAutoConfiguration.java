package com.bankdash.messaging;

import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Wires the outbox for every service that depends on libs/messaging. */
@AutoConfiguration(afterName = {
    "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
    "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
})
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingAutoConfiguration {

    /** Only for services that produce events: they opt in and own an {@code outbox_event} table. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "bankdash.messaging.outbox.enabled", havingValue = "true")
    static class OutboxConfiguration {

        @Bean
        Outbox outbox(JdbcClient jdbc, JsonMapper json, ObjectProvider<Clock> clock) {
            return new Outbox(new JdbcOutboxRepository(jdbc), json, clock.getIfAvailable(Clock::systemUTC));
        }

        /**
         * Uses Boot's template, whose serializers are Strings by default: the outbox row already holds the JSON,
         * so it is sent byte for byte.
         */
        @Bean
        @SuppressWarnings("unchecked")
        OutboxRelay outboxRelay(JdbcClient jdbc, KafkaTemplate<?, ?> kafka, PlatformTransactionManager transactionManager,
                MessagingProperties properties, ObjectProvider<Clock> clock) {
            return new OutboxRelay(new JdbcOutboxRepository(jdbc), (KafkaTemplate<String, String>) kafka,
                    new TransactionTemplate(transactionManager), properties.outbox(), clock.getIfAvailable(Clock::systemUTC));
        }

        @Bean
        OutboxRelayScheduler outboxRelayScheduler(OutboxRelay relay, MessagingProperties properties) {
            return new OutboxRelayScheduler(relay, properties.outbox().pollInterval());
        }
    }
}
