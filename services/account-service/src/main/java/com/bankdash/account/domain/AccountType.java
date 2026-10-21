package com.bankdash.account.domain;

import java.util.Arrays;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public enum AccountType {
    CHECKING,
    SAVINGS;

    /** Exact, case-sensitive match on the name; empty for null or anything else. */
    public static Optional<AccountType> parse(@Nullable String value) {
        return Arrays.stream(values()).filter(type -> type.name().equals(value)).findFirst();
    }
}
