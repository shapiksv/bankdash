package com.bankdash.messaging;

import com.bankdash.events.Topics;
import com.bankdash.events.UserRegistered;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * A listener written the way services write theirs. The payload's {@code fullName} picks a failure mode, so the
 * tests can drive the error handler: {@code always-fails} (a transient error that never goes away) or
 * {@code illegal-argument} (a non-retryable error).
 */
@Component
class RecordingListener {

    static final String CONSUMER = "messaging-it.user-registered";

    /** Every delivery of an event to {@link #on}, counted before anything can fail. */
    final Map<UUID, AtomicInteger> deliveries = new ConcurrentHashMap<>();
    final List<UUID> handled = new CopyOnWriteArrayList<>();

    private final EventPayloads payloads;
    private final IdempotentConsumer idempotentConsumer;

    RecordingListener(EventPayloads payloads, IdempotentConsumer idempotentConsumer) {
        this.payloads = payloads;
        this.idempotentConsumer = idempotentConsumer;
    }

    @KafkaListener(topics = Topics.AUTH_USER_REGISTERED, groupId = "messaging-it")
    void on(ReceivedEvent event) {
        deliveries.computeIfAbsent(event.eventId(), id -> new AtomicInteger()).incrementAndGet();
        UserRegistered payload = payloads.read(event, UserRegistered.class);
        switch (payload.fullName()) {
            case "always-fails" -> throw new IllegalStateException("Database unavailable");
            case "illegal-argument" -> throw new IllegalArgumentException("Cannot handle this user");
            default -> idempotentConsumer.process(CONSUMER, event.eventId(), () -> handled.add(event.eventId()));
        }
    }

    int deliveries(UUID eventId) {
        AtomicInteger count = deliveries.get(eventId);
        return count == null ? 0 : count.get();
    }
}
