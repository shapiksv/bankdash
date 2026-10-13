package com.bankdash.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record UserCredential(
        UUID id, String email, String passwordHash, boolean twoFactorEnabled, UserStatus status, Instant createdAt) {

    public static UserCredential newUser(String email, String passwordHash, Instant now) {
        return new UserCredential(UUID.randomUUID(), email, passwordHash, false, UserStatus.ACTIVE, now);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
