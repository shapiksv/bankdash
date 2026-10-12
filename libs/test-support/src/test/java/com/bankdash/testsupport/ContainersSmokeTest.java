package com.bankdash.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;

/** Proves that {@link ContainersConfig} starts Postgres and Kafka and that Spring Boot connects to both. */
@SpringBootTest(classes = ContainersSmokeTest.SmokeApplication.class)
@Import(ContainersConfig.class)
class ContainersSmokeTest {

    @SpringBootApplication
    static class SmokeApplication {}

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Test
    void contextLoads() {
        assertThat(jdbcTemplate).isNotNull();
        assertThat(kafkaAdmin).isNotNull();
    }

    @Test
    void connectsToPostgres() {
        String version = jdbcTemplate.queryForObject("SHOW server_version", String.class);

        assertThat(version).startsWith("17.");
    }

    @Test
    void connectsToKafka() throws Exception {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            var nodes = admin.describeCluster().nodes().get(30, TimeUnit.SECONDS);

            assertThat(nodes).hasSize(1);
        }
    }
}
