package com.bankdash.account.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A user's money account. All money is USD with scale 2 (docs/architecture.md section 6). */
public record Account(UUID id, UUID userId, AccountType type, String currency, BigDecimal balance, Instant createdAt) {

    public static final String CURRENCY = "USD";

    /** A new, empty account. */
    public static Account open(UUID userId, AccountType type, Instant now) {
        return new Account(UUID.randomUUID(), userId, type, CURRENCY, new BigDecimal("0.00"), now);
    }
}
