package com.bankdash.profile.domain;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Username rules, and the usernames a new user is offered before choosing one on Settings. */
public final class Usernames {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;
    public static final Pattern VALID = Pattern.compile("[a-z0-9._-]{" + MIN_LENGTH + "," + MAX_LENGTH + "}");

    /** Leaves room for a "-" and a 12-character suffix. */
    private static final int MAX_BASE_LENGTH = MAX_LENGTH - 13;

    private Usernames() {}

    /** Usernames are stored lowercased, so uniqueness ignores case. */
    public static String normalize(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Default usernames for a new user, best first: the email's local part, then the same with a suffix taken
     * from the user id, and a last one that is unique on its own. Every candidate matches {@link #VALID}.
     */
    public static Stream<String> candidates(String email, UUID userId) {
        String hex = userId.toString().replace("-", "");
        String base = base(email);
        return Stream.of(base, base + "-" + hex.substring(0, 6), base + "-" + hex.substring(0, 12), "user-" + hex.substring(0, 25));
    }

    private static String base(String email) {
        String local = email.substring(0, Math.max(0, email.indexOf('@'))).toLowerCase(Locale.ROOT);
        String cleaned = local.replaceAll("[^a-z0-9._-]", "").replaceAll("^[._-]+|[._-]+$", "");
        if (cleaned.length() > MAX_BASE_LENGTH) {
            cleaned = cleaned.substring(0, MAX_BASE_LENGTH).replaceAll("[._-]+$", "");
        }
        return cleaned.length() < MIN_LENGTH ? "user" : cleaned;
    }
}
