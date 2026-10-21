package com.bankdash.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.account.AccountIT;
import com.bankdash.events.Topics;
import com.bankdash.testsupport.TopicReader;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

class CardBlockApiIT extends AccountIT {

    @Autowired
    KafkaAdmin kafkaAdmin;

    TopicReader statusChanged;

    @BeforeEach
    void subscribe() {
        statusChanged = new TopicReader(kafkaAdmin.getConfigurationProperties(), Topics.CARDS_STATUS_CHANGED);
    }

    @AfterEach
    void close() {
        statusChanged.close();
    }

    MvcTestResult block(UUID userId, Object cardId) {
        return mvc.post().uri("/cards/{id}/block", cardId).header("Authorization", bearer(userId)).exchange();
    }

    Map<String, Object> card(UUID userId) {
        return jdbc.sql("SELECT * FROM card WHERE user_id = ?").param(userId).query().singleRow();
    }

    long outboxRows(Object cardId) {
        return jdbc.sql("SELECT count(*) FROM outbox_event WHERE message_key = ?").param(cardId.toString())
                .query(Long.class).single();
    }

    @Test
    void ac13_blocksTheCardAndPublishesStatusChanged() {
        UUID userId = registeredUser("Jane Doe");
        Object cardId = card(userId).get("id");
        jdbc.sql("UPDATE card SET status = 'ACTIVE', pin_hash = 'hash' WHERE id = ?").param(cardId).update();

        var response = block(userId, cardId);

        assertThat(response).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(card(userId)).containsEntry("status", "BLOCKED");
        var record = statusChanged.awaitOne(WAIT, r -> cardId.toString().equals(r.key()));
        JsonNode event = json.readTree(record.value());
        assertThat(event.get("eventType").asString()).isEqualTo("CardStatusChanged");
        assertThat(event.get("schemaVersion").asInt()).isOne();
        assertThat(event.get("source").asString()).isEqualTo("account-service");
        assertThat(json.writeValueAsString(event.get("payload"))).isEqualTo("""
                {"cardId":"%s","userId":"%s","status":"BLOCKED"}""".formatted(cardId, userId));
        assertThat(mvc.get().uri("/cards/{id}", cardId).header("Authorization", bearer(userId)).exchange())
                .bodyJson().extractingPath("$.status").isEqualTo("BLOCKED");
    }

    @Test
    void ac13_blocksAnInactiveCard() {
        UUID userId = registeredUser("Jane Doe");
        Object cardId = card(userId).get("id");

        assertThat(block(userId, cardId)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(card(userId)).containsEntry("status", "BLOCKED");
        assertThat(outboxRows(cardId)).isOne();
    }

    @Test
    void ac14_alreadyBlockedIs409AndPublishesNothing() {
        UUID userId = registeredUser("Jane Doe");
        Object cardId = card(userId).get("id");
        assertThat(block(userId, cardId)).hasStatus(HttpStatus.NO_CONTENT);
        statusChanged.awaitOne(WAIT, r -> cardId.toString().equals(r.key()));

        var response = block(userId, cardId);

        assertThat(response).hasStatus(HttpStatus.CONFLICT).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.title").isEqualTo("Card already blocked");
        assertThat(outboxRows(cardId)).isOne();
        assertThat(statusChanged.readFor(Duration.ofSeconds(2), r -> cardId.toString().equals(r.key()))).isEmpty();
    }

    @Test
    void ac5_someoneElsesCardIs404AndStaysUnblocked() {
        UUID jane = registeredUser("Jane Doe");
        UUID john = registeredUser("John Roe");
        Object johnsCard = card(john).get("id");

        var response = block(jane, johnsCard);

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(response).bodyJson().extractingPath("$.title").isEqualTo("Card not found");
        assertThat(card(john)).containsEntry("status", "INACTIVE");
        assertThat(outboxRows(johnsCard)).isZero();
    }
}
