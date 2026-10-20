package com.bankdash.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;

/**
 * The topics of {@code infra/kafka/topics.txt}, created in the test broker. The broker has auto topic creation
 * off, like docker-compose.yml, so a topic that is missing from the file fails the tests that use it.
 */
public final class KafkaTopics {

    /** Set by the bankdash.java-library convention plugin for every test task. */
    static final String REPO_ROOT_PROPERTY = "bankdash.repo-root";

    private final List<NewTopic> topics;

    private KafkaTopics(List<NewTopic> topics) {
        this.topics = topics;
    }

    public List<String> names() {
        return topics.stream().map(NewTopic::name).toList();
    }

    /** Parses {@code <name> <partitions>} lines; blank lines and {@code #} comments are skipped. */
    static KafkaTopics parse(List<String> lines) {
        return new KafkaTopics(lines.stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> line.split("\\s+"))
                .map(parts -> new NewTopic(parts[0], Integer.parseInt(parts[1]), (short) 1))
                .toList());
    }

    static KafkaTopics fromRepository() {
        String root = System.getProperty(REPO_ROOT_PROPERTY);
        if (root == null) {
            throw new IllegalStateException(
                    "System property " + REPO_ROOT_PROPERTY + " is not set; run the tests through Gradle");
        }
        try {
            return parse(Files.readAllLines(Path.of(root, "infra", "kafka", "topics.txt")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Creates every topic; ones that already exist (a container shared by several test contexts) are kept. */
    void createIn(String bootstrapServers) {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            for (NewTopic topic : topics) {
                try {
                    admin.createTopics(List.of(topic)).all().get();
                } catch (ExecutionException e) {
                    if (!(e.getCause() instanceof TopicExistsException)) {
                        throw new IllegalStateException("Could not create topic " + topic.name(), e.getCause());
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
