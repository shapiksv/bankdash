package com.bankdash.account.app;

import com.bankdash.account.domain.Account;
import com.bankdash.account.domain.AccountRepository;
import com.bankdash.account.domain.AccountSummary;
import com.bankdash.account.domain.AccountType;
import com.bankdash.account.domain.Card;
import com.bankdash.account.domain.CardRepository;
import com.bankdash.account.domain.Customer;
import com.bankdash.account.domain.CustomerRepository;
import com.bankdash.events.UserRegistered;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final CardIssuer issuer;
    private final Clock clock;

    AccountService(CustomerRepository customers, AccountRepository accounts, CardRepository cards, CardIssuer issuer,
            Clock clock) {
        this.customers = customers;
        this.accounts = accounts;
        this.cards = cards;
        this.issuer = issuer;
        this.clock = clock;
    }

    /**
     * Opens a CHECKING and a SAVINGS account and an INACTIVE debit card on CHECKING for a newly registered user.
     * A customer that already exists is left as it is, so a replayed event (a new event id for the same user)
     * opens nothing twice.
     */
    @Transactional
    public void openForNewCustomer(UserRegistered event) {
        Instant now = clock.instant();
        if (!customers.insertIfAbsent(new Customer(event.userId(), event.fullName(), now))) {
            log.info("User {} already has accounts", event.userId());
            return;
        }
        Account checking = Account.open(event.userId(), AccountType.CHECKING, now);
        accounts.insert(checking);
        accounts.insert(Account.open(event.userId(), AccountType.SAVINGS, now));
        Card card = issuer.issue(event.userId(), checking.id(), event.fullName(), null);
        cards.insert(card);
        log.info("Opened accounts and card {} for user {}", card.id(), event.userId());
    }

    /** The Accounts page's balances; PENDING while the user's accounts are not opened yet. */
    public AccountSummary summary(UUID userId) {
        return AccountSummary.of(accounts.findByUser(userId));
    }
}
