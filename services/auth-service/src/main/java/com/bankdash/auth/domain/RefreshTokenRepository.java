package com.bankdash.auth.domain;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    void insert(RefreshToken token);

    /** Loads the token and locks its row until the transaction ends. */
    Optional<RefreshToken> findByHashForUpdate(String tokenHash);

    void markReplaced(UUID id, UUID replacedBy);

    void revokeFamily(UUID familyId);

    /** Ends every login of the user, e.g. after a password change. */
    void revokeAllForUser(UUID userId);
}
