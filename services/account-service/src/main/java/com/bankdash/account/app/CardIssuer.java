package com.bankdash.account.app;

import com.bankdash.account.domain.Card;
import com.bankdash.account.domain.CardNumbers;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Issues new cards: generates the number, keeps only its last 4 digits, and dates the expiry. */
@Component
class CardIssuer {

    private final Clock clock;
    private final RandomGenerator random = new SecureRandom();

    CardIssuer(Clock clock) {
        this.clock = clock;
    }

    /** @param pinHash null for an INACTIVE card */
    Card issue(UUID userId, UUID accountId, String holderName, @Nullable String pinHash) {
        String last4 = CardNumbers.last4(CardNumbers.generate(random));
        return Card.issue(userId, accountId, holderName, last4, YearMonth.now(clock), pinHash, clock.instant());
    }
}
