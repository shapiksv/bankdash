package com.bankdash.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.config.ConfigResource;
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

    @Autowired
    KafkaTopics kafkaTopics;

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

    @Test
    void createsTheTopicsFromInfraKafka() throws Exception {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            var existing = admin.listTopics().names().get(30, TimeUnit.SECONDS);

            assertThat(existing).containsAll(kafkaTopics.names()).contains("auth.user-registered.v1.DLT");
        }
    }

    @Test
    void autoTopicCreationIsOffLikeInDockerCompose() throws Exception {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            var node = admin.describeCluster().nodes().get(30, TimeUnit.SECONDS).iterator().next();
            var broker = new ConfigResource(ConfigResource.Type.BROKER, node.idString());
            var value = admin.describeConfigs(List.of(broker)).all().get(30, TimeUnit.SECONDS)
                    .get(broker).get("auto.create.topics.enable").value();

            assertThat(value).isEqualTo("false");
        }
    }

    @Test
    void parsesNamesAndPartitionsAndSkipsComments() {
        var topics = KafkaTopics.parse(List.of("# comment", "", "  a.v1   3", "a.v1.DLT 1"));

        assertThat(topics.names()).containsExactly("a.v1", "a.v1.DLT");
    }
}
