package com.bankdash.auth.app;

/** A password change with the wrong current password. */
public class WrongCurrentPasswordException extends RuntimeException {

    public WrongCurrentPasswordException() {
        super("Current password is incorrect");
    }
}
