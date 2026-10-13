package com.bankdash.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.auth.app.TestAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordHashingTest {

    final PasswordEncoder encoder = new SecurityConfig().passwordEncoder(TestAuthProperties.defaults());

    @Test
    void storesABcryptHashNotThePassword() {
        String hash = encoder.encode("correct horse");

        assertThat(hash).startsWith("$2a$04$").doesNotContain("correct horse");
        assertThat(encoder.matches("correct horse", hash)).isTrue();
        assertThat(encoder.matches("wrong horse", hash)).isFalse();
    }

    @Test
    void saltsEveryHash() {
        assertThat(encoder.encode("correct horse")).isNotEqualTo(encoder.encode("correct horse"));
    }

    @Test
    void usesCost12ByDefault() {
        var production = new SecurityConfig().passwordEncoder(new com.bankdash.auth.app.AuthProperties(
                "i", "a", null, null, null, null, 12, null));

        assertThat(production.encode("correct horse")).startsWith("$2a$12$");
    }
}
