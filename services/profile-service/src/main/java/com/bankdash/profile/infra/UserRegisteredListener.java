package com.bankdash.profile.infra;

import com.bankdash.events.Topics;
import com.bankdash.events.UserRegistered;
import com.bankdash.messaging.EventPayloads;
import com.bankdash.messaging.IdempotentConsumer;
import com.bankdash.messaging.ReceivedEvent;
import com.bankdash.profile.app.ProfileService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * auth.user-registered.v1 → profile and default preferences. Duplicates are skipped by event id; records that
 * cannot be read go to auth.user-registered.v1.DLT (libs/messaging's error handler).
 */
@Component
class UserRegisteredListener {

    static final String CONSUMER = "profile.user-registered";

    private final EventPayloads payloads;
    private final IdempotentConsumer idempotentConsumer;
    private final ProfileService profiles;

    UserRegisteredListener(EventPayloads payloads, IdempotentConsumer idempotentConsumer, ProfileService profiles) {
        this.payloads = payloads;
        this.idempotentConsumer = idempotentConsumer;
        this.profiles = profiles;
    }

    @KafkaListener(topics = Topics.AUTH_USER_REGISTERED)
    void on(ReceivedEvent event) {
        UserRegistered payload = payloads.read(event, UserRegistered.TYPE, UserRegistered.class);
        idempotentConsumer.process(CONSUMER, event.eventId(), () -> profiles.createFromRegistration(payload));
    }
}
