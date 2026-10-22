package com.bankdash.account.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A debit card. {@code status} is the stored one (INACTIVE, ACTIVE or BLOCKED); whether the card has expired depends
 * on the date, so it is derived with {@link #displayStatus}.
 *
 * @param expiry  the card is valid through the last day of this month
 * @param pinHash BCrypt; null while the card is INACTIVE
 */
public record Card(
        UUID id,
        UUID userId,
        UUID accountId,
        CardType type,
        String holderName,
        String last4,
        YearMonth expiry,
        @Nullable String pinHash,
        CardStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static final int VALID_YEARS = 3;

    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("MM/yy");

    /**
     * A new debit card, valid for {@value #VALID_YEARS} years: ACTIVE with a PIN, INACTIVE without one.
     *
     * @param issuedIn the current month, in UTC
     */
    public static Card issue(UUID userId, UUID accountId, String holderName, String last4, YearMonth issuedIn,
            @Nullable String pinHash, Instant now) {
        return new Card(UUID.randomUUID(), userId, accountId, CardType.DEBIT, holderName, last4,
                issuedIn.plusYears(VALID_YEARS), pinHash, pinHash == null ? CardStatus.INACTIVE : CardStatus.ACTIVE,
                now, now);
    }

    public boolean isExpired(YearMonth today) {
        return today.isAfter(expiry);
    }

    public boolean isBlocked() {
        return status == CardStatus.BLOCKED;
    }

    /** What the user sees: BLOCKED wins over EXPIRED, which wins over the stored status. */
    public CardStatus displayStatus(YearMonth today) {
        if (isBlocked()) {
            return CardStatus.BLOCKED;
        }
        return isExpired(today) ? CardStatus.EXPIRED : status;
    }

    /** {@code MM/YY}, as printed on the card. */
    public String expiryText() {
        return expiry.format(EXPIRY_FORMAT);
    }

    /** The card with a new PIN; setting the first PIN activates it. */
    public Card withPin(String newPinHash, Instant now) {
        return new Card(id, userId, accountId, type, holderName, last4, expiry, newPinHash, CardStatus.ACTIVE,
                createdAt, now);
    }

    public Card blocked(Instant now) {
        return new Card(id, userId, accountId, type, holderName, last4, expiry, pinHash, CardStatus.BLOCKED,
                createdAt, now);
    }
}
