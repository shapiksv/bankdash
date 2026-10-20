package com.bankdash.messaging;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Runs {@link OutboxRelay} on its own thread, with a fixed delay between runs. It starts after the context has
 * refreshed and stops before it closes, so it never polls with half-initialized beans. {@link #stop()} waits for
 * a run in progress to end.
 */
class OutboxRelayScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);
    private static final Duration PURGE_INTERVAL = Duration.ofHours(1);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);

    private final OutboxRelay relay;
    private final Duration pollInterval;
    private ScheduledExecutorService executor;

    OutboxRelayScheduler(OutboxRelay relay, Duration pollInterval) {
        this.relay = relay;
        this.pollInterval = pollInterval;
    }

    @Override
    public synchronized void start() {
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("outbox-relay").daemon().factory());
        executor.scheduleWithFixedDelay(() -> run(relay::publishPending), 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
        executor.scheduleWithFixedDelay(() -> run(relay::purgePublished), 1, PURGE_INTERVAL.toMinutes(), TimeUnit.MINUTES);
    }

    /** An exception would cancel the periodic task, so every failure is logged and the next run tries again. */
    private static void run(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.warn("Outbox relay run failed", e);
        }
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    log.warn("Outbox relay run did not end within {}", STOP_TIMEOUT);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return executor != null;
    }
}
