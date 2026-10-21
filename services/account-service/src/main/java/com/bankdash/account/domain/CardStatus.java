package com.bankdash.account.domain;

public enum CardStatus {
    /** No PIN yet. */
    INACTIVE,
    ACTIVE,
    /** Final: there is no unblocking. */
    BLOCKED,
    /** Never stored: derived from the expiry when a card is read (see {@link Card#displayStatus}). */
    EXPIRED
}
