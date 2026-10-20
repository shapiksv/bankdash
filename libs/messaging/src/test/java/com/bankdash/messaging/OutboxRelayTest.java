package com.bankdash.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
    static final Duration SEND_TIMEOUT = Duration.ofMillis(200);
    static final int MAX_ATTEMPTS = 3;

    final OutboxRepository repository = mock();
    @SuppressWarnings("unchecked")
    final KafkaOperations<String, String> kafka = mock(KafkaOperations.class);
    final OutboxRelay relay = new OutboxRelay(repository, kafka, TransactionOperations.withoutTransaction(),
            new MessagingProperties.Outbox(
                    true, Duration.ofMillis(500), 100, SEND_TIMEOUT, MAX_ATTEMPTS, Duration.ofDays(7)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    static PendingRow row(long id) {
        return row(id, "key", 0);
    }

    static PendingRow row(long id, String key, int attempts) {
        return new PendingRow(id, UUID.randomUUID(), "t.v1", key, payload(id), attempts);
    }

    static String payload(long id) {
        return "{\"n\":" + id + "}";
    }

    static CompletableFuture<SendResult<String, String>> ok() {
        return CompletableFuture.completedFuture(null);
    }

    static CompletableFuture<SendResult<String, String>> failed(String message) {
        return CompletableFuture.failedFuture(new RuntimeException(message));
    }

    @Test
    void sendsInOrderAndMarksEachRowPublished() {
        when(repository.lockPending(100)).thenReturn(List.of(row(1), row(2)));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(ok());

        assertThat(relay.publishPending()).isEqualTo(2);

        var inOrder = inOrder(kafka, repository);
        inOrder.verify(kafka).send("t.v1", "key", payload(1));
        inOrder.verify(repository).markPublished(1, NOW);
        inOrder.verify(kafka).send("t.v1", "key", payload(2));
        inOrder.verify(repository).markPublished(2, NOW);
    }

    @Test
    void aFailedSendIsCountedAndStopsTheRunSoNothingOvertakesIt() {
        when(repository.lockPending(100)).thenReturn(List.of(row(1), row(2), row(3, "other", 0)));
        when(kafka.send("t.v1", "key", payload(1))).thenReturn(ok());
        when(kafka.send("t.v1", "key", payload(2))).thenReturn(failed("broker down"));

        assertThat(relay.publishPending()).isEqualTo(1);

        verify(repository).markPublished(1, NOW);
        verify(repository).recordFailure(eq(2L), contains("broker down"), eq(false));
        verify(repository, never()).markPublished(eq(2L), any());
        verify(kafka, never()).send("t.v1", "other", payload(3));
    }

    @Test
    void aSendThatThrowsIsTreatedLikeAFailedOne() {
        when(repository.lockPending(100)).thenReturn(List.of(row(1)));
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("producer closed"));

        assertThat(relay.publishPending()).isZero();
        verify(repository, never()).markPublished(anyLong(), any());
        verify(repository).recordFailure(eq(1L), contains("producer closed"), eq(false));
    }

    @Test
    void aSendThatNeverCompletesEndsTheRunAfterAboutTheSendTimeout() {
        when(repository.lockPending(100)).thenReturn(List.of(row(1), row(2)));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());

        long start = System.nanoTime();
        assertThat(relay.publishPending()).isZero();
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        assertThat(took).isBetween(SEND_TIMEOUT, SEND_TIMEOUT.plus(OutboxRelay.SEND_GRACE).plusSeconds(1));
        verify(repository, never()).markPublished(anyLong(), any());
        verify(repository).recordFailure(eq(1L), contains("TimeoutException"), eq(false));
        verify(kafka, never()).send("t.v1", "key", payload(2));
    }

    @Test
    void theLastFailedAttemptMarksTheRowFailedAndTheRunGoesOnWithTheNextRow() {
        when(repository.lockPending(100)).thenReturn(List.of(row(1, "a", MAX_ATTEMPTS - 1), row(2, "b", 0)));
        when(kafka.send("t.v1", "a", payload(1))).thenReturn(failed("topic missing"));
        when(kafka.send("t.v1", "b", payload(2))).thenReturn(ok());

        assertThat(relay.publishPending()).isOne();

        var inOrder = inOrder(repository);
        inOrder.verify(repository).recordFailure(eq(1L), contains("topic missing"), eq(true));
        inOrder.verify(repository).markPublished(2, NOW);
    }

    @Test
    void aRowThatFailedForGoodHoldsBackLaterRowsWithItsKeyOnly() {
        when(repository.lockPending(100)).thenReturn(List.of(
                row(1, "a", MAX_ATTEMPTS - 1), row(2, "a", 0), row(3, "b", 0), row(4, "a", 0)));
        when(kafka.send("t.v1", "a", payload(1))).thenReturn(failed("topic missing"));
        when(kafka.send(eq("t.v1"), eq("b"), anyString())).thenReturn(ok());

        assertThat(relay.publishPending()).isOne();

        verify(repository).recordFailure(eq(1L), anyString(), eq(true));
        verify(kafka, never()).send("t.v1", "a", payload(2));
        verify(kafka, never()).send("t.v1", "a", payload(4));
        verify(repository).markPublished(3, NOW);
        verify(repository, never()).recordFailure(eq(2L), anyString(), anyBoolean());
    }

    @Test
    void purgeDeletesRowsOlderThanTheRetention() {
        relay.purgePublished();

        verify(repository).deletePublishedBefore(NOW.minus(Duration.ofDays(7)));
    }
}
