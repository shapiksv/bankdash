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

    @Test
    void ac3_limitReturnsTheFirstCardsInCreationOrder() {
        UUID jane = registeredUser("Jane Doe");
        UUID first = (UUID) cardRow(jane).get("id");
        UUID account = (UUID) cardRow(jane).get("account_id");
        for (int i = 1; i <= 2; i++) {
            jdbc.sql("""
                            INSERT INTO card (id, user_id, account_id, type, holder_name, last4, expiry_month, expiry_year,
                                pin_hash, status, created_at, updated_at)
                            VALUES (?, ?, ?, 'DEBIT', 'Jane Doe', '000' || ?, 1, 2099, 'hash', 'ACTIVE',
                                now() + make_interval(secs => ?), now())""")
                    .params(UUID.randomUUID(), jane, account, i, i)
                    .update();
        }

        var all = get(jane, "/cards");
        var two = get(jane, "/cards?limit=2");

        assertThat(all).bodyJson().extractingPath("$.cards[*].maskedNumber").asArray().hasSize(3);
        assertThat(two).bodyJson().extractingPath("$.cards[*].maskedNumber").asArray()
                .containsExactly("**** **** **** " + last4Of(first), "**** **** **** 0001");
        assertThat(get(jane, "/cards?limit=0")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(get(jane, "/cards?limit=101")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    String last4Of(UUID cardId) {
        return jdbc.sql("SELECT last4 FROM card WHERE id = ?").param(cardId).query(String.class).single();
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
