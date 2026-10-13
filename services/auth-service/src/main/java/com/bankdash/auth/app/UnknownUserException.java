package com.bankdash.auth.app;

/** The access token is valid, but its user was deleted or disabled since it was issued. */
public class UnknownUserException extends RuntimeException {

    public UnknownUserException() {
        super("User no longer exists");
    }
}
