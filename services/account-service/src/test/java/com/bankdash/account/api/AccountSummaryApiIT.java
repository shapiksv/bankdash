package com.bankdash.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.account.AccountIT;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class AccountSummaryApiIT extends AccountIT {

    MvcTestResult summary(UUID userId) {
        return mvc.get().uri("/accounts/summary").header("Authorization", bearer(userId)).exchange();
    }

    /** No operation changes a balance yet, so the tests set them directly. */
    void setBalances(UUID userId, String checking, String savings) {
        jdbc.sql("UPDATE account SET balance = CAST(? AS numeric) WHERE user_id = ? AND type = 'CHECKING'")
                .params(checking, userId).update();
        jdbc.sql("UPDATE account SET balance = CAST(? AS numeric) WHERE user_id = ? AND type = 'SAVINGS'")
                .params(savings, userId).update();
    }

    String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    void ac1_returnsCheckingAndSavingsBalances() {
        UUID jane = registeredUser("Jane Doe");
        setBalances(jane, "1250.50", "300.00");

        var response = summary(jane);

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().isStrictlyEqualTo("""
                {"status": "READY", "currency": "USD", "myBalance": "1250.50", "totalSaving": "300.00"}""");
    }

    @Test
    void ac2_amountsAreExactJsonStrings() {
        UUID jane = registeredUser("Jane Doe");
        setBalances(jane, "12345678901234.89", "0.10");

        var response = summary(jane);

        // Compared as raw text: a JSON number, an exponent or a lost trailing zero would all differ.
        assertThat(body(response)).contains("\"myBalance\":\"12345678901234.89\"").contains("\"totalSaving\":\"0.10\"");
    }

    @Test
    void ac3_newCustomerHasZeroBalances() {
        UUID jane = registeredUser("Jane Doe");

        var response = summary(jane);

        assertThat(response).hasStatusOk();
        assertThat(body(response)).contains("\"status\":\"READY\"")
                .contains("\"myBalance\":\"0.00\"").contains("\"totalSaving\":\"0.00\"");
    }

    @Test
    void ac4_unknownCustomerIsPendingWithNullAmounts() {
        var response = summary(UUID.randomUUID());

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().isStrictlyEqualTo("""
                {"status": "PENDING", "currency": "USD", "myBalance": null, "totalSaving": null}""");
    }

    @Test
    void ac5_eachCustomerSeesOnlyTheirOwnBalances() {
        UUID jane = registeredUser("Jane Doe");
        UUID john = registeredUser("John Roe");
        setBalances(jane, "10.00", "20.00");
        setBalances(john, "30.00", "40.00");

        assertThat(summary(jane)).bodyJson().isStrictlyEqualTo("""
                {"status": "READY", "currency": "USD", "myBalance": "10.00", "totalSaving": "20.00"}""");
        assertThat(summary(john)).bodyJson().isStrictlyEqualTo("""
                {"status": "READY", "currency": "USD", "myBalance": "30.00", "totalSaving": "40.00"}""");
    }

    @Test
    void ac6_requestWithoutTokenIs401ProblemDetail() {
        var response = mvc.get().uri("/accounts/summary").exchange();

        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.title").isEqualTo("Unauthorized");
    }

    @Test
    void ac6_requestWithInvalidTokenIs401ProblemDetail() {
        var response = mvc.get().uri("/accounts/summary")
                .header("Authorization", "Bearer " + issuer.expiredToken(UUID.randomUUID())).exchange();

        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType("application/problem+json");
    }

    @Test
    void ac11_onlyCheckingAccountIsPending() {
        UUID jane = registeredUser("Jane Doe");
        setBalances(jane, "5.00", "6.00");
        jdbc.sql("DELETE FROM account WHERE user_id = ? AND type = 'SAVINGS'").param(jane).update();

        var response = summary(jane);

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().isStrictlyEqualTo("""
                {"status": "PENDING", "currency": "USD", "myBalance": null, "totalSaving": null}""");
    }
}
