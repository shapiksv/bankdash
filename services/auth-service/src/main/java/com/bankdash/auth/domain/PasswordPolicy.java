package com.bankdash.auth.domain;

import java.nio.charset.StandardCharsets;

/** At least 8 characters and at most 72 UTF-8 bytes: BCrypt silently ignores everything after byte 72. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {}

    public static void check(String password) {
        check("password", password);
    }

    /** @param field the request field the password came from, for the error response */
    public static void check(String field, String password) {
        if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new InvalidPasswordException(field, "Password must be at least " + MIN_LENGTH + " characters long");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidPasswordException(field, "Password must be at most " + MAX_BYTES + " bytes long");
        }
        if (password.isBlank()) {
            throw new InvalidPasswordException(field, "Password must not be blank");
        }
    }

    public static class InvalidPasswordException extends RuntimeException {

        private final String field;

        public InvalidPasswordException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }
}
