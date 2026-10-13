package com.bankdash.profile.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.profile.ProfileIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class PreferencesApiIT extends ProfileIT {

    MvcTestResult put(UUID userId, String body) {
        return mvc.put().uri("/preferences")
                .header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    @Test
    void newUsersHaveTheDefaults() {
        UUID userId = registeredUser("Jane Doe");

        var response = mvc.get().uri("/preferences").header("Authorization", bearer(userId)).exchange();

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().isStrictlyEqualTo("""
                {"currency": "USD", "timeZone": "UTC", "notifyOnTransfers": true,
                 "notifyOnMerchantOrders": false, "notifyOnRecommendations": true}""");
    }

    @Test
    void savesPreferences() {
        UUID userId = registeredUser("Jane Doe");
        String body = """
                {"currency": "EUR", "timeZone": "Europe/Berlin", "notifyOnTransfers": false,
                 "notifyOnMerchantOrders": true, "notifyOnRecommendations": false}""";

        assertThat(put(userId, body)).hasStatusOk().bodyJson().isStrictlyEqualTo(body);
        assertThat(mvc.get().uri("/preferences").header("Authorization", bearer(userId)).exchange())
                .bodyJson().isStrictlyEqualTo(body);
    }

    @Test
    void unknownCurrencyAndTimeZoneAndMissingFlagsAreFieldErrors() {
        UUID userId = registeredUser("Jane Doe");

        var response = put(userId, """
                {"currency": "BTC", "timeZone": "Mars/Olympus", "notifyOnTransfers": true}""");

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactly("currency", "notifyOnMerchantOrders", "notifyOnRecommendations", "timeZone");
    }

    @Test
    void fixedOffsetsAreNotTimeZones() {
        UUID userId = registeredUser("Jane Doe");

        var response = put(userId, """
                {"currency": "USD", "timeZone": "+02:00", "notifyOnTransfers": true,
                 "notifyOnMerchantOrders": true, "notifyOnRecommendations": true}""");

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response).bodyJson().extractingPath("$.errors[0].message").isEqualTo("Unknown time zone");
    }

    @Test
    void noProfileYetIsA404() {
        var response = put(UUID.randomUUID(), """
                {"currency": "USD", "timeZone": "UTC", "notifyOnTransfers": true,
                 "notifyOnMerchantOrders": true, "notifyOnRecommendations": true}""");

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND).hasContentType("application/problem+json");
    }
}
