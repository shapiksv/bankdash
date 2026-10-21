package com.bankdash.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CardNumbersTest {

    final Random random = new Random(42);

    @Test
    void ac1_generatesSixteenLuhnValidDigits() {
        Set<String> numbers = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String number = CardNumbers.generate(random);

            assertThat(number).hasSize(16).matches("4[0-9]{15}");
            assertThat(CardNumbers.isLuhnValid(number)).as(number).isTrue();
            numbers.add(number);
        }
        assertThat(numbers).hasSizeGreaterThan(990);
    }

    @Test
    void luhnCheckMatchesAKnownTestNumber() {
        assertThat(CardNumbers.isLuhnValid("4111111111111111")).isTrue();
        assertThat(CardNumbers.isLuhnValid("4111111111111112")).isFalse();
    }

    @Test
    void ac3_masksAllButLastFour() {
        String number = "4111222233335678";

        assertThat(CardNumbers.last4(number)).isEqualTo("5678");
        assertThat(CardNumbers.mask(CardNumbers.last4(number))).isEqualTo("**** **** **** 5678");
    }
}
