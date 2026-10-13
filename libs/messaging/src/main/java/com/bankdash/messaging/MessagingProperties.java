package com.bankdash.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code bankdash.messaging.*}.
 *
 * @param outbox   the transactional outbox of a service that produces events
 * @param consumer error handling of every Kafka listener
 */
@ConfigurationProperties("bankdash.messaging")
public record MessagingProperties(@DefaultValue Outbox outbox, @DefaultValue Consumer consumer) {

    /**
     * @param enabled      whether this service has an outbox (its {@code outbox_event} table) and runs the relay
     * @param pollInterval pause between two relay runs
     * @param batchSize    rows sent per relay run
     * @param sendTimeout  how long the relay waits for the broker to acknowledge one record
     * @param retention    how long published rows are kept before they are deleted
     */
    public record Outbox(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("500ms") Duration pollInterval,
            @DefaultValue("100") int batchSize,
            @DefaultValue("10s") Duration sendTimeout,
            @DefaultValue("7d") Duration retention) {}

    /**
     * @param retryInterval pause before each retry of a failed record
     * @param retries       retries after the first attempt, before the record goes to its {@code .DLT}
     */
    public record Consumer(@DefaultValue("1s") Duration retryInterval, @DefaultValue("3") int retries) {}
}
