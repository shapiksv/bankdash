package com.bankdash.account.app;

import com.bankdash.account.domain.Account;
import com.bankdash.account.domain.AccountNotFoundException;
import com.bankdash.account.domain.AccountRepository;
import com.bankdash.account.domain.AccountType;
import com.bankdash.account.domain.Card;
import com.bankdash.account.domain.CardAlreadyBlockedException;
import com.bankdash.account.domain.CardLimitReachedException;
import com.bankdash.account.domain.CardNotFoundException;
import com.bankdash.account.domain.CardNotUsableException;
import com.bankdash.account.domain.CardRepository;
import com.bankdash.account.domain.CardStatus;
import com.bankdash.account.domain.Customer;
import com.bankdash.account.domain.CustomerRepository;
import com.bankdash.account.domain.PinPolicy;
import com.bankdash.account.domain.PinRule;
import com.bankdash.account.domain.WeakPinException;
import com.bankdash.events.CardStatusChanged;
import com.bankdash.events.Topics;
import com.bankdash.messaging.Outbox;
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

    /** Cards a user may have that are not blocked. */
    public static final int MAX_CARDS = 5;

    private final CardRepository cards;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final CardIssuer issuer;
    private final PasswordEncoder pinEncoder;
    private final Outbox outbox;
    private final Clock clock;

    CardService(CardRepository cards, CustomerRepository customers, AccountRepository accounts, CardIssuer issuer,
            PasswordEncoder pinEncoder, Outbox outbox, Clock clock) {
        this.cards = cards;
        this.customers = customers;
        this.accounts = accounts;
        this.issuer = issuer;
        this.pinEncoder = pinEncoder;
        this.outbox = outbox;
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
     * Issues an ACTIVE debit card with {@code pin} on the user's account of {@code accountType}. The holder name is
     * the name the user registered with.
     *
     * @throws AccountNotFoundException if the user's accounts have not been opened yet
     * @throws CardLimitReachedException if the user already has {@value #MAX_CARDS} cards that are not blocked
     * @throws WeakPinException if the PIN breaks a {@link PinRule}
     */
    @Transactional
    public Card add(UUID userId, AccountType accountType, String pin) {
        Customer customer = customers.lockById(userId)
                .orElseThrow(() -> new AccountNotFoundException(userId, accountType));
        Account account = accounts.findByUserAndType(userId, accountType)
                .orElseThrow(() -> new AccountNotFoundException(userId, accountType));
        if (cards.countNonBlocked(userId) >= MAX_CARDS) {
            throw new CardLimitReachedException(MAX_CARDS);
        }
        PinPolicy.requireStrong(pin);
        Card card = issuer.issue(userId, account.id(), customer.fullName(), pinEncoder.encode(pin));
        cards.insert(card);
        return card;
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

    /**
     * Blocks the card for good and emits cards.status-changed.v1 in the same transaction. An expired card can still
     * be blocked.
     *
     * @throws CardAlreadyBlockedException if it was blocked before; nothing is emitted then
     */
    @Transactional
    public void block(UUID userId, UUID cardId) {
        Card card = cards.lockOwned(cardId, userId).orElseThrow(() -> new CardNotFoundException(cardId));
        if (card.isBlocked()) {
            throw new CardAlreadyBlockedException(cardId);
        }
        cards.update(card.blocked(clock.instant()));
        outbox.publish(Topics.CARDS_STATUS_CHANGED, cardId.toString(), CardStatusChanged.TYPE,
                CardStatusChanged.SCHEMA_VERSION, new CardStatusChanged(cardId, userId, CardStatus.BLOCKED.name()));
    }

    /** The current month in UTC, which decides whether a card has expired. */
    public YearMonth today() {
        return YearMonth.now(clock);
    }
}
