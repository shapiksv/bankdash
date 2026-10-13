package com.bankdash.auth.app;

/** Unknown email, wrong password or a disabled account. Deliberately indistinguishable. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
