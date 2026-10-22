package com.bankdash.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PinPolicyTest {

    @ParameterizedTest(name = "AC10: {0} breaks {1}")
    @CsvSource({
        // The spec's examples (AC10)
        "0000, ALL_SAME_DIGITS",
        "1234, CONSECUTIVE_DIGITS",
        "9876, CONSECUTIVE_DIGITS",
        "1212, REPEATING_PAIR",
        "1998, LOOKS_LIKE_YEAR",
        "2580, KEYPAD_PATTERN",
        "abcd, NOT_FOUR_DIGITS",
        "123, NOT_FOUR_DIGITS",
        // Each rule at its edges
        "12345, NOT_FOUR_DIGITS",
        "'', NOT_FOUR_DIGITS",
        "'12 4', NOT_FOUR_DIGITS",
        "١٢٣٤, NOT_FOUR_DIGITS",
        "9999, ALL_SAME_DIGITS",
        "0123, CONSECUTIVE_DIGITS",
        "6789, CONSECUTIVE_DIGITS",
        "3210, CONSECUTIVE_DIGITS",
        "6969, REPEATING_PAIR",
        "2020, REPEATING_PAIR",
        "1950, LOOKS_LIKE_YEAR",
        "2029, LOOKS_LIKE_YEAR",
        "0852, KEYPAD_PATTERN",
        "1470, KEYPAD_PATTERN",
        "0741, KEYPAD_PATTERN",
        "3690, KEYPAD_PATTERN",
        "0963, KEYPAD_PATTERN",
        "1590, KEYPAD_PATTERN",
        "0951, KEYPAD_PATTERN",
        "3570, KEYPAD_PATTERN",
        "0753, KEYPAD_PATTERN",
        "1593, KEYPAD_PATTERN",
        "3951, KEYPAD_PATTERN",
    })
    void ac10_rejectsWeakPin(String pin, PinRule rule) {
        assertThat(PinPolicy.check(pin)).contains(rule);
        assertThatExceptionOfType(WeakPinException.class)
                .isThrownBy(() -> PinPolicy.requireStrong(pin))
                .withMessage(rule.message())
                .satisfies(e -> assertThat(e.rule()).isEqualTo(rule));
    }

    @ParameterizedTest(name = "AC10: {0} is strong enough")
    @ValueSource(strings = {"4826", "7391", "1949", "2030", "0192", "8901", "1213", "1122"})
    void ac10_acceptsStrongPin(String pin) {
        assertThat(PinPolicy.check(pin)).isEmpty();
        assertThatCode(() -> PinPolicy.requireStrong(pin)).doesNotThrowAnyException();
    }

    @Test
    void ac10_rulesAreCheckedInSpecOrder() {
        // 0000 is also an ABAB pair, 2020 also a year: the earlier rule wins.
        assertThat(PinPolicy.check("0000")).contains(PinRule.ALL_SAME_DIGITS);
        assertThat(PinPolicy.check("2020")).contains(PinRule.REPEATING_PAIR);
    }

    @Test
    void ac11_sameAsCurrentHasItsOwnRule() {
        assertThat(PinRule.SAME_AS_CURRENT.message()).isEqualTo("New PIN must differ from the current PIN");
        assertThat(PinRule.CONSECUTIVE_DIGITS.message())
                .as("the spec's example detail").isEqualTo("PIN must not be a sequence of consecutive digits");
    }
}
