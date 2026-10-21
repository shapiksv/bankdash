package com.bankdash.account.domain;

/** The card is blocked or has expired, so its PIN can no longer be changed. */
public class CardNotUsableException extends RuntimeException {

    public CardNotUsableException(String message) {
        super(message);
    }
}
