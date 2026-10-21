package com.bankdash.account.domain;

import java.util.Optional;
import java.util.Set;

/**
 * Rejects PINs that are easy to guess. The rules are checked in {@link PinRule} order and the first one that
 * fails is reported; {@link PinRule#SAME_AS_CURRENT} is left to the caller, which has the current hash.
 */
public final class PinPolicy {

    public static final int FIRST_YEAR = 1950;
    public static final int LAST_YEAR = 2029;

    /** Straight lines on the 3×4 keypad (1-2-3 / 4-5-6 / 7-8-9 / 0 below 8), in both directions. */
    static final Set<String> KEYPAD_LINES = Set.of(
            "2580", "0852", "1470", "0741", "3690", "0963", "1590", "0951", "3570", "0753", "1593", "3951");

    private PinPolicy() {}

    /** @return the first rule {@code pin} breaks, or empty if it is strong enough */
    public static Optional<PinRule> check(String pin) {
        if (!isFourDigits(pin)) {
            return Optional.of(PinRule.NOT_FOUR_DIGITS);
        }
        if (pin.chars().distinct().count() == 1) {
            return Optional.of(PinRule.ALL_SAME_DIGITS);
        }
        if (isConsecutive(pin, 1) || isConsecutive(pin, -1)) {
            return Optional.of(PinRule.CONSECUTIVE_DIGITS);
        }
        if (pin.charAt(0) == pin.charAt(2) && pin.charAt(1) == pin.charAt(3)) {
            return Optional.of(PinRule.REPEATING_PAIR);
        }
        int number = Integer.parseInt(pin);
        if (number >= FIRST_YEAR && number <= LAST_YEAR) {
            return Optional.of(PinRule.LOOKS_LIKE_YEAR);
        }
        if (KEYPAD_LINES.contains(pin)) {
            return Optional.of(PinRule.KEYPAD_PATTERN);
        }
        return Optional.empty();
    }

    /** @throws WeakPinException with the first rule {@code pin} breaks */
    public static void requireStrong(String pin) {
        check(pin).ifPresent(rule -> {
            throw new WeakPinException(rule);
        });
    }

    /** ASCII digits only: {@link Character#isDigit} would also accept e.g. Arabic-Indic digits. */
    private static boolean isFourDigits(String pin) {
        return pin.length() == 4 && pin.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    private static boolean isConsecutive(String pin, int step) {
        for (int i = 1; i < pin.length(); i++) {
            if (pin.charAt(i) - pin.charAt(i - 1) != step) {
                return false;
            }
        }
        return true;
    }
}
