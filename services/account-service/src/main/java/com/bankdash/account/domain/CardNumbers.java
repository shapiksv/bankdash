package com.bankdash.account.domain;

import java.util.random.RandomGenerator;

/**
 * Fake card numbers (no payment network). A number exists only while a card is issued: account-service stores and
 * returns its last 4 digits only.
 */
public final class CardNumbers {

    public static final int LENGTH = 16;

    private CardNumbers() {}

    /** A 16-digit number starting with 4, with a valid Luhn check digit, so it looks like a real one. */
    public static String generate(RandomGenerator random) {
        StringBuilder number = new StringBuilder("4");
        while (number.length() < LENGTH - 1) {
            number.append(random.nextInt(10));
        }
        return number.append(checkDigit(number)).toString();
    }

    public static String last4(String number) {
        return number.substring(number.length() - 4);
    }

    /** The only form a card number is ever shown in, e.g. {@code **** **** **** 5678}. */
    public static String mask(String last4) {
        return "**** **** **** " + last4;
    }

    static boolean isLuhnValid(String number) {
        return checkDigit(number.substring(0, number.length() - 1)) == number.charAt(number.length() - 1) - '0';
    }

    /** The Luhn digit to append to {@code payload}: double every second digit, starting from the right. */
    private static int checkDigit(CharSequence payload) {
        int sum = 0;
        for (int i = 0; i < payload.length(); i++) {
            int digit = payload.charAt(payload.length() - 1 - i) - '0';
            if (i % 2 == 0) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return (10 - sum % 10) % 10;
    }
}
