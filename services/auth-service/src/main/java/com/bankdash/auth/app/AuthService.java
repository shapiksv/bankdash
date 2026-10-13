package com.bankdash.auth.app;

import com.bankdash.auth.domain.Emails;
import com.bankdash.auth.domain.PasswordPolicy;
import com.bankdash.auth.domain.RefreshToken;
import com.bankdash.auth.domain.RefreshTokenRepository;
import com.bankdash.auth.domain.UserCredential;
import com.bankdash.auth.domain.UserCredentialRepository;
import com.bankdash.events.Topics;
import com.bankdash.events.UserRegistered;
import com.bankdash.messaging.Outbox;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserCredentialRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokens;
    private final AuthProperties properties;
    private final Outbox outbox;
    private final Clock clock;
    /** Compared against when the email is unknown, so both cases cost one BCrypt check. */
    private final String dummyHash;

    public AuthService(
            UserCredentialRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            AccessTokenIssuer accessTokens,
            AuthProperties properties,
            Outbox outbox,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.properties = properties;
        this.outbox = outbox;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /**
     * Registers the user and logs them in. {@code fullName} is not stored here, because auth does not own it: it
     * travels in {@link UserRegistered}, which is written to the outbox in the same transaction, so the event
     * exists exactly when the user does.
     */
    @Transactional
    public Session signup(String fullName, String email, String password) {
        PasswordPolicy.check(password);
        UserCredential user = UserCredential.newUser(Emails.normalize(email), passwordEncoder.encode(password), clock.instant());
        users.insert(user);
        outbox.publish(Topics.AUTH_USER_REGISTERED, user.id().toString(), UserRegistered.TYPE,
                new UserRegistered(user.id(), user.email(), fullName.strip()));
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

    /**
     * Rotates the refresh token: the presented token is marked as replaced and a new one in the same family
     * is issued. Presenting a token that was already replaced or revoked means it was copied, so the whole
     * family is revoked (reuse detection). Commits even when it throws, so the revocation sticks.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Session refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken presented = refreshTokens.findByHashForUpdate(OpaqueTokens.sha256Hex(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (presented.revoked() || presented.replacedBy() != null) {
            refreshTokens.revokeFamily(presented.familyId());
            log.warn("Refresh token reuse for user {}: revoked token family {}", presented.userId(), presented.familyId());
            throw new InvalidRefreshTokenException();
        }
        if (!now.isBefore(presented.expiresAt())) {
            throw new InvalidRefreshTokenException();
        }
        Optional<UserCredential> user = users.findById(presented.userId()).filter(UserCredential::isActive);
        if (user.isEmpty()) {
            refreshTokens.revokeFamily(presented.familyId());
            throw new InvalidRefreshTokenException();
        }

        Issued next = issue(user.get(), presented.familyId(), now, presented.familyExpiresAt());
        refreshTokens.markReplaced(presented.id(), next.tokenId());
        return next.session();
    }

    /** Ends the login the token belongs to. Unknown tokens are ignored: logout always succeeds. */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByHashForUpdate(OpaqueTokens.sha256Hex(rawRefreshToken))
                .ifPresent(token -> refreshTokens.revokeFamily(token.familyId()));
    }

    public Optional<UserCredential> findUser(UUID id) {
        return users.findById(id);
    }

    /** A new token family: the start of one login. */
    private Session startSession(UserCredential user) {
        Instant now = clock.instant();
        Instant familyExpiresAt = now.plus(properties.refreshFamilyTtl());
        return issue(user, UUID.randomUUID(), now, familyExpiresAt).session();
    }

    /** A refresh token that lives 7 days from now, but never past the end of its family. */
    private Issued issue(UserCredential user, UUID familyId, Instant now, Instant familyExpiresAt) {
        String raw = OpaqueTokens.generate();
        Instant expiresAt = min(now.plus(properties.refreshTokenTtl()), familyExpiresAt);
        UUID tokenId = UUID.randomUUID();
        refreshTokens.insert(new RefreshToken(
                tokenId, user.id(), familyId, OpaqueTokens.sha256Hex(raw), now, expiresAt, familyExpiresAt, null, false));
        return new Issued(tokenId, new Session(accessTokens.issue(user.id(), user.email()), raw, expiresAt));
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private record Issued(UUID tokenId, Session session) {}
}
