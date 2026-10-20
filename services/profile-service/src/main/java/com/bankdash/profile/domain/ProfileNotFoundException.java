package com.bankdash.profile.domain;

import java.util.UUID;

/** The user has no profile (yet): it is created asynchronously from the user-registered event. */
public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(UUID userId) {
        super("No profile for user " + userId);
    }
}
