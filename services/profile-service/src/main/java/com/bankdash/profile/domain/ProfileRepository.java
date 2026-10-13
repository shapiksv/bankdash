package com.bankdash.profile.domain;

import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository {

    /** @return false if the user already has a profile (nothing is changed then) */
    boolean insertIfAbsent(UserProfile profile);

    boolean usernameTaken(String username);

    Optional<UserProfile> findById(UUID userId);

    /** @throws UsernameTakenException if another user has the username */
    void update(UserProfile profile);
}
