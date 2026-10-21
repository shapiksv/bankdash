package com.bankdash.account.domain;

import java.util.UUID;

/** The user has no account of that type (yet): accounts are opened asynchronously from the user-registered event. */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID userId, AccountType type) {
        super("No " + type + " account for user " + userId);
    }
}
