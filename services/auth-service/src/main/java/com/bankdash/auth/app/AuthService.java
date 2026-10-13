package com.bankdash.auth.app;

import com.bankdash.auth.domain.Emails;
import com.bankdash.auth.domain.PasswordPolicy;
import com.bankdash.auth.domain.RefreshToken;
import com.bankdash.auth.domain.RefreshTokenRepository;
import com.bankdash.auth.domain.UserCredential;
import com.bankdash.auth.domain.UserCredentialRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserCredentialRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokens;
    private final AuthProperties properties;
    private final Clock clock;
    /** Compared against when the email is unknown, so both cases cost one BCrypt check. */
    private final String dummyHash;

    public AuthService(
            UserCredentialRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            AccessTokenIssuer accessTokens,
            AuthProperties properties,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /**
     * Registers the user and logs them in. {@code fullName} is validated by the API but not stored here:
     * auth does not own it. It goes into the user-registered event once the outbox exists (step 3).
     */
    @Transactional
    public Session signup(String email, String password) {
        PasswordPolicy.check(password);
        UserCredential user = UserCredential.newUser(Emails.normalize(email), passwordEncoder.encode(password), clock.instant());
        users.insert(user);
        return startSession(user);
    }

    @Transactional
    public Session login(String email, String password) {
        var user = users.findByEmail(Emails.normalize(email));
        if (user.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(password, user.get().passwordHash()) || !user.get().isActive()) {
            throw new InvalidCredentialsException();
        }
        return startSession(user.get());
    }

    /** A new token family: the start of one login. */
    private Session startSession(UserCredential user) {
        Instant now = clock.instant();
        Instant familyExpiresAt = now.plus(properties.refreshFamilyTtl());
        return issue(user, UUID.randomUUID(), now, familyExpiresAt);
    }

    private Session issue(UserCredential user, UUID familyId, Instant now, Instant familyExpiresAt) {
        String raw = OpaqueTokens.generate();
        Instant expiresAt = min(now.plus(properties.refreshTokenTtl()), familyExpiresAt);
        refreshTokens.insert(new RefreshToken(
                UUID.randomUUID(), user.id(), familyId, OpaqueTokens.sha256Hex(raw), now, expiresAt, familyExpiresAt, null, false));
        return new Session(accessTokens.issue(user.id(), user.email()), raw, expiresAt);
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
