package com.bankdash.profile.domain;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** What Settings → Edit Profile saves. Blank optional fields are stored as null. */
public record ProfileChanges(
        String fullName,
        String username,
        @Nullable LocalDate dateOfBirth,
        @Nullable String presentAddress,
        @Nullable String permanentAddress,
        @Nullable String city,
        @Nullable String postalCode,
        @Nullable String country) {}
