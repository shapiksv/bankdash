package com.bankdash.auth.domain;

import java.util.Optional;
import java.util.UUID;

public interface UserCredentialRepository {

    /** @throws EmailAlreadyRegisteredException if the (normalized) email is taken */
    void insert(UserCredential user);

    Optional<UserCredential> findByEmail(String normalizedEmail);

    Optional<UserCredential> findById(UUID id);

    void updatePasswordHash(UUID id, String passwordHash);

    void updateTwoFactorEnabled(UUID id, boolean enabled);
}
