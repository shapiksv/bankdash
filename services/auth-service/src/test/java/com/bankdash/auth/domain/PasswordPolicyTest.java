package com.bankdash.auth.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.bankdash.auth.domain.PasswordPolicy.InvalidPasswordException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsEightCharacters() {
        assertThatCode(() -> PasswordPolicy.check("abcdefgh")).doesNotThrowAnyException();
    }

    @Test
    void rejectsSevenCharacters() {
        assertThatExceptionOfType(InvalidPasswordException.class)
                .isThrownBy(() -> PasswordPolicy.check("abcdefg"))
                .withMessageContaining("at least 8");
    }

    @Test
    void acceptsExactly72Bytes() {
        assertThatCode(() -> PasswordPolicy.check("a".repeat(72))).doesNotThrowAnyException();
    }

    @Test
    void rejects73Bytes() {
        assertThatExceptionOfType(InvalidPasswordException.class)
                .isThrownBy(() -> PasswordPolicy.check("a".repeat(73)))
                .withMessageContaining("72 bytes");
    }

    @Test
    void countsBytesNotCharacters() {
        // 25 characters but 75 UTF-8 bytes: BCrypt would ignore the tail.
        assertThatExceptionOfType(InvalidPasswordException.class)
                .isThrownBy(() -> PasswordPolicy.check("€".repeat(25)));
    }

    @Test
    void rejectsBlankPasswords() {
        assertThatExceptionOfType(InvalidPasswordException.class).isThrownBy(() -> PasswordPolicy.check("          "));
    }
}
