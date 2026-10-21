package com.bankdash.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.account.AccountIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class CardPinApiIT extends AccountIT {

    @Autowired
    PasswordEncoder pinEncoder;

    MvcTestResult setPin(UUID userId, Object cardId, String pin) {
        return mvc.put().uri("/cards/{id}/pin", cardId)
                .header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("pin", pin)))
                .exchange();
    }

    Map<String, Object> card(UUID userId) {
        return jdbc.sql("SELECT * FROM card WHERE user_id = ?").param(userId).query().singleRow();
    }

    /** A registered user whose first card is ACTIVE with PIN 4826. */
    UUID userWithActiveCard() {
        UUID userId = registeredUser("Jane Doe");
        assertThat(setPin(userId, card(userId).get("id"), "4826")).hasStatus(HttpStatus.NO_CONTENT);
        return userId;
    }

    @Test
    void ac8_firstPinActivatesTheCardAndIsStoredHashed() {
        UUID userId = registeredUser("Jane Doe");
        Object cardId = card(userId).get("id");

        var response = setPin(userId, cardId, "4826");

        assertThat(response).hasStatus(HttpStatus.NO_CONTENT);
        var card = card(userId);
        assertThat(card).containsEntry("status", "ACTIVE");
        String hash = (String) card.get("pin_hash");
        assertThat(hash).startsWith("$2").doesNotContain("4826");
        assertThat(pinEncoder.matches("4826", hash)).isTrue();
        assertThat(mvc.get().uri("/cards/{id}", cardId).header("Authorization", bearer(userId)).exchange())
                .bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    void ac9_changesThePin() {
        UUID userId = userWithActiveCard();
        String oldHash = (String) card(userId).get("pin_hash");

        var response = setPin(userId, card(userId).get("id"), "7391");

        assertThat(response).hasStatus(HttpStatus.NO_CONTENT);
        String newHash = (String) card(userId).get("pin_hash");
        assertThat(newHash).isNotEqualTo(oldHash);
        assertThat(pinEncoder.matches("7391", newHash)).isTrue();
        assertThat(card(userId)).containsEntry("status", "ACTIVE");
    }

    @ParameterizedTest(name = "AC10: {0} is rejected as {1}")
    @CsvSource({
        "0000, ALL_SAME_DIGITS, PIN must not repeat one digit",
        "1234, CONSECUTIVE_DIGITS, PIN must not be a sequence of consecutive digits",
        "9876, CONSECUTIVE_DIGITS, PIN must not be a sequence of consecutive digits",
        "1212, REPEATING_PAIR, PIN must not be a repeating pair of digits",
        "1998, LOOKS_LIKE_YEAR, PIN must not look like a year",
        "2580, KEYPAD_PATTERN, PIN must not be a line on the keypad",
        "abcd, NOT_FOUR_DIGITS, PIN must be exactly 4 digits",
        "123, NOT_FOUR_DIGITS, PIN must be exactly 4 digits",
    })
    void ac10_weakPinIs422WithTheRuleAndPinUnchanged(String pin, String rule, String detail) {
        UUID userId = userWithActiveCard();
        String hash = (String) card(userId).get("pin_hash");

        var response = setPin(userId, card(userId).get("id"), pin);

        assertThat(response).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.title").isEqualTo("Weak PIN");
        assertThat(response).bodyJson().extractingPath("$.rule").isEqualTo(rule);
        assertThat(response).bodyJson().extractingPath("$.detail").isEqualTo(detail);
        assertThat(card(userId)).containsEntry("pin_hash", hash);
    }

    @Test
    void ac10_weakFirstPinLeavesTheCardInactive() {
        UUID userId = registeredUser("Jane Doe");

        assertThat(setPin(userId, card(userId).get("id"), "1111")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        assertThat(card(userId)).containsEntry("status", "INACTIVE").containsEntry("pin_hash", null);
    }

    @Test
    void ac11_samePinAsCurrentIs422() {
        UUID userId = userWithActiveCard();
        String hash = (String) card(userId).get("pin_hash");

        var response = setPin(userId, card(userId).get("id"), "4826");

        assertThat(response).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response).bodyJson().extractingPath("$.rule").isEqualTo("SAME_AS_CURRENT");
        assertThat(card(userId)).containsEntry("pin_hash", hash);
    }

    @Test
    void ac12_blockedCardIs409() {
        UUID userId = userWithActiveCard();
        Object cardId = card(userId).get("id");
        jdbc.sql("UPDATE card SET status = 'BLOCKED' WHERE id = ?").param(cardId).update();
        String hash = (String) card(userId).get("pin_hash");

        var response = setPin(userId, cardId, "7391");

        assertThat(response).hasStatus(HttpStatus.CONFLICT).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.detail").isEqualTo("The card is blocked");
        assertThat(card(userId)).containsEntry("pin_hash", hash).containsEntry("status", "BLOCKED");
        // The card's state is reported before anything about the PIN.
        assertThat(setPin(userId, cardId, "1234")).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void ac12_expiredCardIs409() {
        UUID userId = registeredUser("Jane Doe");
        Object cardId = card(userId).get("id");
        jdbc.sql("UPDATE card SET expiry_year = 2020 WHERE id = ?").param(cardId).update();

        var response = setPin(userId, cardId, "7391");

        assertThat(response).hasStatus(HttpStatus.CONFLICT);
        assertThat(response).bodyJson().extractingPath("$.detail").isEqualTo("The card has expired");
        assertThat(card(userId)).containsEntry("status", "INACTIVE").containsEntry("pin_hash", null);
    }

    @Test
    void ac5_someoneElsesCardIs404() {
        UUID jane = registeredUser("Jane Doe");
        UUID john = registeredUser("John Roe");

        var response = setPin(jane, card(john).get("id"), "7391");

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(response).bodyJson().extractingPath("$.title").isEqualTo("Card not found");
        assertThat(card(john)).containsEntry("status", "INACTIVE").containsEntry("pin_hash", null);
    }

    @Test
    void missingPinIs400() {
        UUID userId = registeredUser("Jane Doe");

        var response = mvc.put().uri("/cards/{id}/pin", card(userId).get("id"))
                .header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response).bodyJson().extractingPath("$.errors[0].field").isEqualTo("pin");
    }
}
