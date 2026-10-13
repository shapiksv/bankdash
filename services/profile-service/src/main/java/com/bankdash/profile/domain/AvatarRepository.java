package com.bankdash.profile.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AvatarRepository {

    /** Inserts or replaces the user's avatar. */
    void save(Avatar avatar);

    Optional<Avatar> findById(UUID userId);

    /** When the avatar last changed, without loading the image; empty if there is none. */
    Optional<Instant> updatedAt(UUID userId);
}
