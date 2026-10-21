package com.bankdash.account.app;

import com.bankdash.account.domain.Card;
import com.bankdash.account.domain.CardNotFoundException;
import com.bankdash.account.domain.CardRepository;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** The user's cards. Every lookup is scoped to {@code userId}, the caller from the access token. */
@Service
public class CardService {

    private final CardRepository cards;
    private final Clock clock;

    CardService(CardRepository cards, Clock clock) {
        this.cards = cards;
        this.clock = clock;
    }

    /** @param limit at most this many cards, oldest first; all of them if null */
    public List<Card> list(UUID userId, @Nullable Integer limit) {
        return cards.findByUser(userId, limit == null ? Integer.MAX_VALUE : limit);
    }

    public Card get(UUID userId, UUID cardId) {
        return cards.findOwned(cardId, userId).orElseThrow(() -> new CardNotFoundException(cardId));
    }

    /** The current month in UTC, which decides whether a card has expired. */
    public YearMonth today() {
        return YearMonth.now(clock);
    }
}
