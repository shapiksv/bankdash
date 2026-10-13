package com.bankdash.auth.domain;

import java.util.Locale;

public final class Emails {

    private Emails() {}

    /** Emails are unique case-insensitively: stored trimmed and lowercased. */
    public static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
