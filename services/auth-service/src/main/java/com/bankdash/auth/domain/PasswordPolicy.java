package com.bankdash.auth.domain;

import java.nio.charset.StandardCharsets;

/** At least 8 characters and at most 72 UTF-8 bytes: BCrypt silently ignores everything after byte 72. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {}

    public static void check(String password) {
        if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new InvalidPasswordException("Password must be at least " + MIN_LENGTH + " characters long");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidPasswordException("Password must be at most " + MAX_BYTES + " bytes long");
        }
        if (password.isBlank()) {
            throw new InvalidPasswordException("Password must not be blank");
        }
    }

    public static class InvalidPasswordException extends RuntimeException {

        public InvalidPasswordException(String message) {
            super(message);
        }
    }
}
