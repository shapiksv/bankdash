package com.bankdash.profile.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The Edit Profile data of one user. Everything but the name and username is optional. */
public record UserProfile(
        UUID userId,
        String fullName,
        String username,
        @Nullable LocalDate dateOfBirth,
        @Nullable String presentAddress,
        @Nullable String permanentAddress,
        @Nullable String city,
        @Nullable String postalCode,
        @Nullable String country,
        Instant createdAt,
        Instant updatedAt) {

    /** A new user's profile: what the user-registered event carries, the rest is filled in on Settings. */
    public static UserProfile registered(UUID userId, String fullName, String username, Instant now) {
        return new UserProfile(userId, fullName, username, null, null, null, null, null, null, now, now);
    }
}
