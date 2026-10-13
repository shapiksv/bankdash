package com.bankdash.profile.domain;

import java.util.Optional;
import java.util.UUID;

public interface PreferencesRepository {

    void insertIfAbsent(Preferences preferences);

    Optional<Preferences> findById(UUID userId);

    void update(Preferences preferences);
}
