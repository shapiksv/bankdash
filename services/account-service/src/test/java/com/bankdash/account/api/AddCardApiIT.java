package com.bankdash.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.account.AccountIT;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class AddCardApiIT extends AccountIT {

    @Autowired
    PasswordEncoder pinEncoder;

    MvcTestResult add(UUID userId, String accountType, String pin) {
        return add(userId, json.writeValueAsString(Map.of("accountType", accountType, "pin", pin)));
    }

    MvcTestResult add(UUID userId, String body) {
        return mvc.post().uri("/cards")
                .header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    UUID accountId(UUID userId, String type) {
        return jdbc.sql("SELECT id FROM account WHERE user_id = ? AND type = ?").params(userId, type)
                .query(UUID.class).single();
    }

    Map<String, Object> cardRow(Object cardId) {
        return jdbc.sql("SELECT * FROM card WHERE id = ?::uuid").param(cardId.toString()).query().singleRow();
    }

    long cards(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM card WHERE user_id = ?").param(userId).query(Long.class).single();
    }

    long nonBlockedCards(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM card WHERE user_id = ? AND status <> 'BLOCKED'").param(userId)
                .query(Long.class).single();
    }

    @Test
    void ac6_addsAnActiveCardToTheCheckingAccount() {
        UUID userId = registeredUser("Jane Doe");

        var response = add(userId, "CHECKING", "4826");

        assertThat(response).hasStatus(HttpStatus.CREATED);
        assertThat(response).bodyJson().extractingPath("$.type").isEqualTo("DEBIT");
        assertThat(response).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(response).bodyJson().extractingPath("$.accountId").isEqualTo(accountId(userId, "CHECKING").toString());
        assertThat(response).bodyJson().extractingPath("$.maskedNumber").asString().matches("\\*{4} \\*{4} \\*{4} [0-9]{4}");
        String id = json.readTree(response.getResponse().getContentAsByteArray()).get("id").asString();
        assertThat(response.getResponse().getHeader("Location")).isEqualTo("/cards/" + id);
        assertThat(pinEncoder.matches("4826", (String) cardRow(id).get("pin_hash"))).isTrue();
        assertThat(cards(userId)).isEqualTo(2);
    }

    @Test
    void ac6_addsAnActiveCardToTheSavingsAccount() {
        UUID userId = registeredUser("Jane Doe");

        var response = add(userId, "SAVINGS", "7391");

        assertThat(response).hasStatus(HttpStatus.CREATED);
        assertThat(response).bodyJson().extractingPath("$.accountId").isEqualTo(accountId(userId, "SAVINGS").toString());
        assertThat(response).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    void ac6_holderNameComesFromTheRegisteredCustomer() {
        UUID userId = registeredUser("Charlene Reed");

        var response = add(userId, "SAVINGS", "7391");

        assertThat(response).bodyJson().extractingPath("$.holderName").isEqualTo("Charlene Reed");
    }

    @Test
    void ac10_weakPinOnAddIs422AndNoCardIsCreated() {
        UUID userId = registeredUser("Jane Doe");

        var response = add(userId, "CHECKING", "1234");

        assertThat(response).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response).bodyJson().extractingPath("$.rule").isEqualTo("CONSECUTIVE_DIGITS");
        assertThat(cards(userId)).isOne();
    }

    @Test
    void ac16_sixthNonBlockedCardIs409() {
        UUID userId = registeredUser("Jane Doe");
        for (int i = 0; i < 4; i++) {
            assertThat(add(userId, "CHECKING", "4826")).hasStatus(HttpStatus.CREATED);
        }

        var response = add(userId, "SAVINGS", "4826");

        assertThat(response).hasStatus(HttpStatus.CONFLICT).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.title").isEqualTo("Card limit reached");
        assertThat(cards(userId)).isEqualTo(5);
    }

    @Test
    void ac16_blockedCardsDoNotCountTowardsTheLimit() {
        UUID userId = registeredUser("Jane Doe");
        for (int i = 0; i < 4; i++) {
            add(userId, "CHECKING", "4826");
        }
        jdbc.sql("UPDATE card SET status = 'BLOCKED' WHERE id = (SELECT id FROM card WHERE user_id = ? LIMIT 1)")
                .param(userId).update();

        assertThat(add(userId, "SAVINGS", "4826")).hasStatus(HttpStatus.CREATED);
        assertThat(add(userId, "SAVINGS", "4826")).hasStatus(HttpStatus.CONFLICT);
        assertThat(nonBlockedCards(userId)).isEqualTo(5);
    }

    @Test
    void ac16_concurrentAddsNeverExceedTheLimit() throws Exception {
        UUID userId = registeredUser("Jane Doe");
        for (int i = 0; i < 3; i++) {
            add(userId, "CHECKING", "4826");
        }

        List<Future<MvcTestResult>> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 6; i++) {
                results.add(executor.submit((Callable<MvcTestResult>) () -> add(userId, "SAVINGS", "7391")));
            }
        }

        List<Integer> statuses = new ArrayList<>();
        for (var result : results) {
            statuses.add(result.get().getResponse().getStatus());
        }
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(5);
        assertThat(nonBlockedCards(userId)).isEqualTo(5);
    }

    @Test
    void ac19_noAccountsYetIs404() {
        UUID userId = UUID.randomUUID();

        var response = add(userId, "CHECKING", "4826");

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.title").isEqualTo("Account not found");
        assertThat(cards(userId)).isZero();
    }

    @Test
    void unknownAccountTypeIs400() {
        UUID userId = registeredUser("Jane Doe");

        assertThat(add(userId, "CREDIT", "4826")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(add(userId, """
                {"pin": "4826"}""")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(cards(userId)).isOne();
    }
}
