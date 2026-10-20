package com.bankdash.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.bankdash.testsupport.ContainersConfig;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(ContainersConfig.class)
class IdempotentConsumerIT {

    @Autowired
    IdempotentConsumer consumer;

    final UUID eventId = UUID.randomUUID();
    final AtomicInteger runs = new AtomicInteger();

    @Test
    void runsTheHandlerOncePerEvent() {
        assertThat(consumer.process("test.a", eventId, runs::incrementAndGet)).isTrue();
        assertThat(consumer.process("test.a", eventId, runs::incrementAndGet)).isFalse();

        assertThat(runs).hasValue(1);
    }

    @Test
    void eachConsumerProcessesTheEventOnItsOwn() {
        consumer.process("test.a", eventId, runs::incrementAndGet);
        consumer.process("test.b", eventId, runs::incrementAndGet);

        assertThat(runs).hasValue(2);
    }

    @Test
    void aFailedHandlerLeavesTheEventUnprocessedSoARetryRunsIt() {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->
                consumer.process("test.a", eventId, () -> {
                    runs.incrementAndGet();
                    throw new IllegalStateException("Database unavailable");
                }));

        assertThat(consumer.process("test.a", eventId, runs::incrementAndGet)).isTrue();
        assertThat(runs).hasValue(2);
    }
}
