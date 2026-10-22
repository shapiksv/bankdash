package com.bankdash.account.domain;

/**
 * The PIN rules of the BANK-4 spec (Scope §9), in the order they are checked. The name is sent to clients as the
 * ProblemDetail's {@code rule}, so it must not change.
 */
public enum PinRule {
    NOT_FOUR_DIGITS("PIN must be exactly 4 digits"),
    ALL_SAME_DIGITS("PIN must not repeat one digit"),
    CONSECUTIVE_DIGITS("PIN must not be a sequence of consecutive digits"),
    REPEATING_PAIR("PIN must not be a repeating pair of digits"),
    LOOKS_LIKE_YEAR("PIN must not look like a year"),
    KEYPAD_PATTERN("PIN must not be a line on the keypad"),
    /** Needs the current PIN's hash, so the card service checks it after {@link PinPolicy#check}. */
    SAME_AS_CURRENT("New PIN must differ from the current PIN");

    private final String message;

    PinRule(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
