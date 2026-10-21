package com.bankdash.events;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * account-service changed a card's status. Topic {@link Topics#CARDS_STATUS_CHANGED}, key {@code cardId}.
 *
 * @param status the new status; {@code BLOCKED} for now. A String rather than an enum, so that a status added later
 *     is a compatible change that existing consumers can still read
 */
public record CardStatusChanged(@NotNull UUID cardId, @NotNull UUID userId, @NotBlank String status) {

    public static final String TYPE = "CardStatusChanged";
    public static final int SCHEMA_VERSION = 1;
}
