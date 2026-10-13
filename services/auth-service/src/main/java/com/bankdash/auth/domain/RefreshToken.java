package com.bankdash.auth.domain;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One refresh token of a login ("family"). Only the SHA-256 hash of the opaque value is stored.
 *
 * @param replacedBy the token that replaced this one on refresh; a used token must never be presented again
 */
public record RefreshToken(
        UUID id,
        UUID userId,
        UUID familyId,
        String tokenHash,
        Instant createdAt,
        Instant expiresAt,
        Instant familyExpiresAt,
        @Nullable UUID replacedBy,
        boolean revoked) {}
