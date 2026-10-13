package com.bankdash.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UsernamesTest {

    static final UUID USER = UUID.fromString("0123abcd-4567-89ef-0123-456789abcdef");

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "jane.doe@example.com,         jane.doe",
        "Jane.Doe+bank@example.com,    jane.doebank",
        "._jane_.@example.com,         jane",
        "j@example.com,                user",
        "émile@example.com,            mile",
        "a-very-long-local-part-for-sure@example.com, a-very-long-local",
    })
    void theFirstCandidateComesFromTheEmail(String email, String expected) {
        assertThat(Usernames.candidates(email, USER).findFirst()).hasValue(expected);
    }

    @Test
    void laterCandidatesAddPartsOfTheUserId() {
        assertThat(Usernames.candidates("jane@example.com", USER).toList()).containsExactly(
                "jane", "jane-0123ab", "jane-0123abcd4567", "user-0123abcd456789ef012345678");
    }

    @Test
    void everyCandidateIsAValidUsername() {
        for (String email : new String[] {"a-very-long-local-part-for-sure@example.com", "x@example.com", "..@example.com"}) {
            assertThat(Usernames.candidates(email, USER)).allMatch(c -> Usernames.VALID.matcher(c).matches(), email);
        }
    }

    @Test
    void normalizeLowercasesAndTrims() {
        assertThat(Usernames.normalize("  Jane.Doe ")).isEqualTo("jane.doe");
    }
}
