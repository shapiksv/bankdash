package com.bankdash.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankdash.auth.app.AccessTokenIssuer.AccessToken;
import com.bankdash.auth.domain.RefreshToken;
import com.bankdash.auth.domain.RefreshTokenRepository;
import com.bankdash.auth.domain.UserCredential;
import com.bankdash.auth.domain.UserCredentialRepository;
import com.bankdash.auth.domain.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    final UserCredentialRepository users = mock();
    final RefreshTokenRepository refreshTokens = mock();
    final PasswordEncoder passwordEncoder = mock();
    final AccessTokenIssuer accessTokens = mock();
    AuthService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenAnswer(call -> "hash:" + call.getArgument(0));
        when(accessTokens.issue(any(), anyString())).thenReturn(new AccessToken("jwt", Duration.ofMinutes(15)));
        service = new AuthService(users, refreshTokens, passwordEncoder, accessTokens,
                TestAuthProperties.defaults(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void unknownEmailStillCostsOnePasswordCheck() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> service.login("nobody@example.com", "whatever1"));
        verify(passwordEncoder).matches(eq("whatever1"), eq("hash:not-a-real-password"));
    }

    @Test
    void disabledUserCannotLogIn() {
        var user = new UserCredential(UUID.randomUUID(), "jane@example.com", "h", false, UserStatus.DISABLED, NOW);
        when(users.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "h")).thenReturn(true);

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> service.login("jane@example.com", "secret123"));
    }

    @Test
    void signupStoresANormalizedEmailAndStartsATokenFamily() {
        Session session = service.signup("  Jane@Example.com", "secret123");

        var user = ArgumentCaptor.forClass(UserCredential.class);
        verify(users).insert(user.capture());
        assertThat(user.getValue().email()).isEqualTo("jane@example.com");
        assertThat(user.getValue().passwordHash()).isEqualTo("hash:secret123");

        var token = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).insert(token.capture());
        assertThat(token.getValue().userId()).isEqualTo(user.getValue().id());
        assertThat(token.getValue().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(token.getValue().familyExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(token.getValue().tokenHash()).isEqualTo(OpaqueTokens.sha256Hex(session.refreshToken()));
        assertThat(session.refreshTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    final UserCredential jane = new UserCredential(UUID.randomUUID(), "jane@example.com", "h", false, UserStatus.ACTIVE, NOW);

    RefreshToken stored(String raw, Instant expiresAt, Instant familyExpiresAt, UUID replacedBy, boolean revoked) {
        var token = new RefreshToken(UUID.randomUUID(), jane.id(), UUID.randomUUID(), OpaqueTokens.sha256Hex(raw),
                NOW.minus(Duration.ofDays(1)), expiresAt, familyExpiresAt, replacedBy, revoked);
        when(refreshTokens.findByHashForUpdate(OpaqueTokens.sha256Hex(raw))).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void refreshRotatesWithinTheSameFamily() {
        when(users.findById(jane.id())).thenReturn(Optional.of(jane));
        var old = stored("old", NOW.plus(Duration.ofDays(6)), NOW.plus(Duration.ofDays(29)), null, false);

        Session session = service.refresh("old");

        var next = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).insert(next.capture());
        assertThat(next.getValue().familyId()).isEqualTo(old.familyId());
        assertThat(next.getValue().familyExpiresAt()).isEqualTo(old.familyExpiresAt());
        assertThat(next.getValue().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(session.refreshToken()).isNotEqualTo("old");
        verify(refreshTokens).markReplaced(old.id(), next.getValue().id());
    }

    @Test
    void refreshNeverOutlivesTheFamily() {
        when(users.findById(jane.id())).thenReturn(Optional.of(jane));
        Instant familyEnd = NOW.plus(Duration.ofDays(2));
        stored("old", familyEnd, familyEnd, null, false);

        Session session = service.refresh("old");

        assertThat(session.refreshTokenExpiresAt()).isEqualTo(familyEnd);
    }

    @Test
    void reusingAReplacedTokenRevokesTheFamily() {
        var old = stored("old", NOW.plus(Duration.ofDays(6)), NOW.plus(Duration.ofDays(29)), UUID.randomUUID(), false);

        assertThatExceptionOfType(InvalidRefreshTokenException.class).isThrownBy(() -> service.refresh("old"));
        verify(refreshTokens).revokeFamily(old.familyId());
        verify(refreshTokens, never()).insert(any());
    }

    @Test
    void aRevokedTokenIsRejected() {
        var old = stored("old", NOW.plus(Duration.ofDays(6)), NOW.plus(Duration.ofDays(29)), null, true);

        assertThatExceptionOfType(InvalidRefreshTokenException.class).isThrownBy(() -> service.refresh("old"));
        verify(refreshTokens).revokeFamily(old.familyId());
    }

    @Test
    void anExpiredTokenIsRejectedWithoutRevokingTheFamily() {
        stored("old", NOW, NOW.plus(Duration.ofDays(29)), null, false);

        assertThatExceptionOfType(InvalidRefreshTokenException.class).isThrownBy(() -> service.refresh("old"));
        verify(refreshTokens, never()).revokeFamily(any());
        verify(refreshTokens, never()).insert(any());
    }

    @Test
    void anUnknownTokenIsRejected() {
        when(refreshTokens.findByHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidRefreshTokenException.class).isThrownBy(() -> service.refresh("nope"));
    }
}
