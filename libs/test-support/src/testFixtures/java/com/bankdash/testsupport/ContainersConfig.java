package com.bankdash.testsupport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres and Kafka for integration tests, wired into Spring Boot via {@link ServiceConnection}.
 *
 * <p>Import it with {@code @Import(ContainersConfig.class)} on a {@code @SpringBootTest}. Spring's test
 * context cache reuses the containers across test classes that share the same configuration.
 *
 * <p>Each container only starts when the service has the matching client on its classpath, so a service
 * without Kafka gets Postgres only.
 *
 * <p>Image versions and broker settings match {@code docker-compose.yml}: auto topic creation is off, and the
 * topics of {@code infra/kafka/topics.txt} are created before any listener or producer starts.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    public static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17.11");
    public static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka:4.3.1");

    @Bean
    @ServiceConnection
    @ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
    public PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }

    /** A nested class, so that its Kafka client types are only loaded when Kafka is on the classpath. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
    static class Kafka {

        @Bean
        @ServiceConnection
        KafkaContainer kafkaContainer() {
            return new KafkaContainer(KAFKA_IMAGE).withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
        }

        /** A singleton, so it runs before listener containers and schedulers start (after the context refresh). */
        @Bean
        KafkaTopics kafkaTopics(KafkaContainer kafka) {
            KafkaTopics topics = KafkaTopics.fromRepository();
            topics.createIn(kafka.getBootstrapServers());
            return topics;
        }
    }
}
