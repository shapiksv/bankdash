package com.bankdash.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.account.AccountIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class CardReadApiIT extends AccountIT {

    MvcTestResult get(UUID userId, String uri) {
        return mvc.get().uri(uri).header("Authorization", bearer(userId)).exchange();
    }

    Map<String, Object> cardRow(UUID userId) {
        return jdbc.sql("SELECT * FROM card WHERE user_id = ?").param(userId).query().singleRow();
    }

    @Test
    void ac3_listsOnlyOwnCardsWithMaskedNumbers() throws Exception {
        UUID jane = registeredUser("Jane Doe");
        UUID john = registeredUser("John Roe");
        var janesCard = cardRow(jane);

        var response = get(jane, "/cards");

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.cards[*].id").asArray()
                .containsExactly(janesCard.get("id").toString());
        assertThat(response).bodyJson().extractingPath("$.cards[0].maskedNumber")
                .isEqualTo("**** **** **** " + janesCard.get("last4"));
        assertThat(response).bodyJson().extractingPath("$.cards[0].holderName").isEqualTo("Jane Doe");
        assertThat(response.getResponse().getContentAsString())
                .doesNotContain(cardRow(john).get("id").toString())
                .doesNotContainIgnoringCase("pin")
                .doesNotContainPattern("[0-9]{16}");
    }

    /** Adds a card created {@code secondsLater} after now, so the tests control the creation order. */
    void insertCard(UUID userId, UUID accountId, String last4, String status, int secondsLater) {
        jdbc.sql("""
                        INSERT INTO card (id, user_id, account_id, type, holder_name, last4, expiry_month, expiry_year,
                            pin_hash, status, created_at, updated_at)
                        VALUES (?, ?, ?, 'DEBIT', 'Jane Doe', ?, 1, 2099, 'hash', ?,
                            now() + make_interval(secs => ?), now())""")
                .params(UUID.randomUUID(), userId, accountId, last4, status, secondsLater)
                .update();
    }

    /** A user whose INACTIVE registration card is the oldest, followed by BLOCKED/ACTIVE cards in turn. */
    record MixedCards(UUID userId, String registrationLast4) {}

    MixedCards userWithMixedCards() {
        UUID jane = registeredUser("Jane Doe");
        var registration = cardRow(jane);
        UUID account = (UUID) registration.get("account_id");
        insertCard(jane, account, "0001", "BLOCKED", 1);
        insertCard(jane, account, "0002", "ACTIVE", 2);
        insertCard(jane, account, "0003", "BLOCKED", 3);
        insertCard(jane, account, "0004", "ACTIVE", 4);
        return new MixedCards(jane, (String) registration.get("last4"));
    }

    static String masked(String last4) {
        return "**** **** **** " + last4;
    }

    @Test
    void ac20_nonBlockedNewestFirstThenBlockedNewestFirst() {
        MixedCards jane = userWithMixedCards();

        var response = get(jane.userId(), "/cards");

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.cards[*].maskedNumber").asArray().containsExactly(
                masked("0004"), masked("0002"), masked(jane.registrationLast4()), masked("0003"), masked("0001"));
        assertThat(response).bodyJson().extractingPath("$.cards[*].status").asArray()
                .containsExactly("ACTIVE", "ACTIVE", "INACTIVE", "BLOCKED", "BLOCKED");
    }

    @Test
    void ac20_limitReturnsTheFirstCardsOfThatOrder() {
        MixedCards jane = userWithMixedCards();

        assertThat(get(jane.userId(), "/cards?limit=2")).bodyJson().extractingPath("$.cards[*].maskedNumber").asArray()
                .containsExactly(masked("0004"), masked("0002"));
        assertThat(get(jane.userId(), "/cards?limit=4")).bodyJson().extractingPath("$.cards[*].maskedNumber").asArray()
                .containsExactly(masked("0004"), masked("0002"), masked(jane.registrationLast4()), masked("0003"));
        assertThat(get(jane.userId(), "/cards?limit=0")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(get(jane.userId(), "/cards?limit=101")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void ac3_noCardsYetIsAnEmptyList() {
        var response = get(UUID.randomUUID(), "/cards");

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().isEqualTo("""
                {"cards": []}""");
    }

    @Test
    void ac4_returnsCardDetails() {
        UUID jane = registeredUser("Jane Doe");
        var row = cardRow(jane);

        var response = get(jane, "/cards/" + row.get("id"));

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().isEqualTo("""
                {"id": "%s", "accountId": "%s", "type": "DEBIT", "maskedNumber": "**** **** **** %s",
                 "holderName": "Jane Doe", "expiry": "%02d/%02d", "status": "INACTIVE"}""".formatted(
                row.get("id"), row.get("account_id"), row.get("last4"), row.get("expiry_month"),
                (Integer) row.get("expiry_year") % 100));
    }

    @Test
    void ac4_reportsExpiredCardAsExpired() {
        UUID jane = registeredUser("Jane Doe");
        Object cardId = cardRow(jane).get("id");
        jdbc.sql("UPDATE card SET expiry_year = 2020, expiry_month = 12 WHERE id = ?").param(cardId).update();

        var response = get(jane, "/cards/" + cardId);

        assertThat(response).bodyJson().extractingPath("$.status").isEqualTo("EXPIRED");
        assertThat(response).bodyJson().extractingPath("$.expiry").isEqualTo("12/20");
    }

    @Test
    void ac5_someoneElsesCardIs404LikeAMissingOne() {
        UUID jane = registeredUser("Jane Doe");
        UUID john = registeredUser("John Roe");

        var foreign = get(jane, "/cards/" + cardRow(john).get("id"));
        var missing = get(jane, "/cards/" + UUID.randomUUID());

        assertThat(foreign).hasStatus(HttpStatus.NOT_FOUND).hasContentType("application/problem+json");
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND).hasContentType("application/problem+json");
        assertThat(foreign).bodyJson().extractingPath("$.title").isEqualTo("Card not found");
        // Only the requested path differs.
        assertThat(withoutInstance(foreign)).isEqualTo(withoutInstance(missing));
    }

    String withoutInstance(MvcTestResult result) {
        Map<?, ?> body = json.readValue(result.getResponse().getContentAsByteArray(), Map.class);
        body.remove("instance");
        return body.toString();
    }

    @Test
    void ac18_requestWithoutTokenIs401ProblemDetail() {
        var response = mvc.get().uri("/cards").exchange();

        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.title").isEqualTo("Unauthorized");
    }
}
