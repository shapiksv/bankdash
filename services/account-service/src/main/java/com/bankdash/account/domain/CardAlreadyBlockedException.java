package com.bankdash.account.domain;

import java.util.UUID;

public class CardAlreadyBlockedException extends RuntimeException {

    public CardAlreadyBlockedException(UUID cardId) {
        super("Card " + cardId + " is already blocked");
    }
}
