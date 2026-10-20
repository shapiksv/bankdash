package com.bankdash.events;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** auth-service committed a new user. Topic {@link Topics#AUTH_USER_REGISTERED}, key {@code userId}. */
public record UserRegistered(@NotNull UUID userId, @NotBlank String email, @NotBlank String fullName) {

    public static final String TYPE = "UserRegistered";
    public static final int SCHEMA_VERSION = 1;
}
