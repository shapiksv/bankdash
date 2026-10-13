package com.bankdash.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankdash.messaging.OutboxRepository.PendingRow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionOperations;

class OutboxRelayTest {

    static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    final OutboxRepository repository = mock();
    @SuppressWarnings("unchecked")
    final KafkaOperations<String, String> kafka = mock(KafkaOperations.class);
    final OutboxRelay relay = new OutboxRelay(repository, kafka, TransactionOperations.withoutTransaction(),
            new MessagingProperties.Outbox(true, Duration.ofMillis(500), 100, Duration.ofSeconds(1), Duration.ofDays(7)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    static PendingRow row(long id) {
        return new PendingRow(id, UUID.randomUUID(), "t.v1", "key", "{\"n\":" + id + "}");
    }

    static CompletableFuture<SendResult<String, String>> ok() {
        return CompletableFuture.completedFuture(null);
    }

    @Test
    void sendsInOrderAndMarksEachRowPublished() {
        when(repository.lockPending(100)).thenReturn(List.of(row(1), row(2)));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(ok());

        assertThat(relay.publishPending()).isEqualTo(2);

        var inOrder = org.mockito.Mockito.inOrder(kafka, repository);
        inOrder.verify(kafka).send("t.v1", "key", "{\"n\":1}");
        inOrder.verify(repository).markPublished(1, NOW);
        inOrder.verify(kafka).send("t.v1", "key", "{\"n\":2}");
        inOrder.verify(repository).markPublished(2, NOW);
    }

    @Test
    void aFailedSendStopsTheRunSoNothingOvertakesIt() {
        when(repository.lockPending(100)).thenReturn(List.of(row(1), row(2), row(3)));
        when(kafka.send("t.v1", "key", "{\"n\":1}")).thenReturn(ok());
        when(kafka.send("t.v1", "key", "{\"n\":2}")).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThat(relay.publishPending()).isEqualTo(1);

        verify(repository).markPublished(1, NOW);
        verify(repository, never()).markPublished(eq(2L), any());
        verify(kafka, never()).send("t.v1", "key", "{\"n\":3}");
    }

    @Test
    void aSendThatThrowsIsTreatedLikeAFailedOne() {
        when(repository.lockPending(100)).thenReturn(List.of(row(1)));
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("producer closed"));

        assertThat(relay.publishPending()).isZero();
        verify(repository, never()).markPublished(anyLong(), any());
    }

    @Test
    void purgeDeletesRowsOlderThanTheRetention() {
        relay.purgePublished();

        verify(repository).deletePublishedBefore(NOW.minus(Duration.ofDays(7)));
    }
}
