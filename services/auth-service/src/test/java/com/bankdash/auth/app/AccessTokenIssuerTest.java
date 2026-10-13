package com.bankdash.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.bankdash.auth.infra.JwtTestDecoders;
import com.bankdash.auth.infra.RsaKeys;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AccessTokenIssuerTest {

    static final RSAKey KEY = RsaKeys.generate();
    static final UUID USER_ID = UUID.fromString("0b9e7f43-2a43-4a8e-9c3f-3f1c9f4f2a10");

    final AuthProperties properties = TestAuthProperties.defaults();

    AccessTokenIssuer issuerAt(Instant now) {
        return new AccessTokenIssuer(
                new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY))),
                KEY,
                properties,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void issuesAnRs256TokenWithTheExpectedClaims() {
        Instant now = Instant.now();

        var token = issuerAt(now).issue(USER_ID, "jane@example.com");
        Jwt jwt = JwtTestDecoders.forKey(KEY).decode(token.value());

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", KEY.getKeyID());
        assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("bankdash-auth");
        assertThat(jwt.getAudience()).containsExactly("bankdash-api");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("jane@example.com");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getIssuedAt()).isEqualTo(now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plus(Duration.ofMinutes(15)));
        assertThat(token.expiresIn()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void everyTokenHasItsOwnId() {
        var issuer = issuerAt(Instant.now());

        assertThat(issuer.issue(USER_ID, "a@example.com").value())
                .isNotEqualTo(issuer.issue(USER_ID, "a@example.com").value());
    }

    @Test
    void anExpiredTokenIsRejected() {
        // Issued 16 minutes ago: the 15-minute lifetime is over, even with the 60 s clock skew.
        var token = issuerAt(Instant.now().minus(Duration.ofMinutes(16))).issue(USER_ID, "jane@example.com");

        assertThatExceptionOfType(JwtValidationException.class)
                .isThrownBy(() -> JwtTestDecoders.forKey(KEY).decode(token.value()))
                .withMessageContaining("expired");
    }

    @Test
    void aTokenFromAnotherKeyIsRejected() {
        var token = issuerAt(Instant.now()).issue(USER_ID, "jane@example.com");

        assertThatExceptionOfType(org.springframework.security.oauth2.jwt.BadJwtException.class)
                .isThrownBy(() -> JwtTestDecoders.forKey(RsaKeys.generate()).decode(token.value()));
    }
}
