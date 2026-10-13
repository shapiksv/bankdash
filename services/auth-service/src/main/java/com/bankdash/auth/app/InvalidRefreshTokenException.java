package com.bankdash.auth.app;

/** Missing, unknown, expired, revoked or reused refresh token. The client must log in again. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or expired");
    }
}
