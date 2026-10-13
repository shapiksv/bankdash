package com.bankdash.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres and Kafka for integration tests, wired into Spring Boot via {@link ServiceConnection}.
 *
 * <p>Import it with {@code @Import(ContainersConfig.class)} on a {@code @SpringBootTest}. Spring's test
 * context cache reuses the containers across test classes that share the same configuration.
 *
 * <p>Image versions match {@code docker-compose.yml}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    public static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17.11");
    public static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka:4.3.1");

    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }

    @Bean
    @ServiceConnection
    public KafkaContainer kafkaContainer() {
        return new KafkaContainer(KAFKA_IMAGE);
    }
}
