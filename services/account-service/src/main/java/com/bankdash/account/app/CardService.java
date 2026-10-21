package com.bankdash.account.app;

import com.bankdash.account.domain.Card;
import com.bankdash.account.domain.CardNotFoundException;
import com.bankdash.account.domain.CardNotUsableException;
import com.bankdash.account.domain.CardRepository;
import com.bankdash.account.domain.PinPolicy;
import com.bankdash.account.domain.PinRule;
import com.bankdash.account.domain.WeakPinException;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user's cards. Every lookup is scoped to {@code userId}, the caller from the access token. Commands check the
 * card first (404), then its state (409), then the input (422).
 */
@Service
public class CardService {

    private final CardRepository cards;
    private final PasswordEncoder pinEncoder;
    private final Clock clock;

    CardService(CardRepository cards, PasswordEncoder pinEncoder, Clock clock) {
        this.cards = cards;
        this.pinEncoder = pinEncoder;
        this.clock = clock;
    }

    /** @param limit at most this many cards, oldest first; all of them if null */
    public List<Card> list(UUID userId, @Nullable Integer limit) {
        return cards.findByUser(userId, limit == null ? Integer.MAX_VALUE : limit);
    }

    public Card get(UUID userId, UUID cardId) {
        return cards.findOwned(cardId, userId).orElseThrow(() -> new CardNotFoundException(cardId));
    }

    /**
     * Sets the first PIN, which activates the card, or replaces the current one.
     *
     * @throws CardNotUsableException if the card is blocked or has expired
     * @throws WeakPinException if the PIN breaks a {@link PinRule}, including being the current PIN
     */
    @Transactional
    public void setPin(UUID userId, UUID cardId, String pin) {
        Card card = cards.lockOwned(cardId, userId).orElseThrow(() -> new CardNotFoundException(cardId));
        if (card.isBlocked()) {
            throw new CardNotUsableException("The card is blocked");
        }
        if (card.isExpired(today())) {
            throw new CardNotUsableException("The card has expired");
        }
        PinPolicy.requireStrong(pin);
        if (card.pinHash() != null && pinEncoder.matches(pin, card.pinHash())) {
            throw new WeakPinException(PinRule.SAME_AS_CURRENT);
        }
        cards.update(card.withPin(pinEncoder.encode(pin), clock.instant()));
    }

    /** The current month in UTC, which decides whether a card has expired. */
    public YearMonth today() {
        return YearMonth.now(clock);
    }
}
