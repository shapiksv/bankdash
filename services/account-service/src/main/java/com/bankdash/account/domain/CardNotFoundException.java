package com.bankdash.account.domain;

import java.util.UUID;

/** The card does not exist or belongs to another user: both look the same to the caller. */
public class CardNotFoundException extends RuntimeException {

    public CardNotFoundException(UUID cardId) {
        super("No card " + cardId + " for this user");
    }
}
