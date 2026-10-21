package com.bankdash.account.infra;

import com.bankdash.account.app.AccountService;
import com.bankdash.events.Topics;
import com.bankdash.events.UserRegistered;
import com.bankdash.messaging.EventPayloads;
import com.bankdash.messaging.IdempotentConsumer;
import com.bankdash.messaging.ReceivedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * auth.user-registered.v1 → customer, CHECKING and SAVINGS accounts and a first card. Duplicates are skipped by
 * event id; records that cannot be read go to auth.user-registered.v1.DLT (libs/messaging's error handler).
 */
@Component
class UserRegisteredListener {

    static final String CONSUMER = "account.user-registered";

    private final EventPayloads payloads;
    private final IdempotentConsumer idempotentConsumer;
    private final AccountService accounts;

    UserRegisteredListener(EventPayloads payloads, IdempotentConsumer idempotentConsumer, AccountService accounts) {
        this.payloads = payloads;
        this.idempotentConsumer = idempotentConsumer;
        this.accounts = accounts;
    }

    @KafkaListener(topics = Topics.AUTH_USER_REGISTERED)
    void on(ReceivedEvent event) {
        UserRegistered payload = payloads.read(event, UserRegistered.TYPE, UserRegistered.class);
        idempotentConsumer.process(CONSUMER, event.eventId(), () -> accounts.openForNewCustomer(payload));
    }
}
