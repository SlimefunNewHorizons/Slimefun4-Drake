package com.github.drakescraft_labs.slimefun4.core.services.github;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class GitHubTaskShutdownTest {
    @Test
    void returnsCompletedLookup() throws Exception {
        assertEquals("texture", GitHubTask.awaitWhileRunning(
                CompletableFuture.completedFuture("texture"), () -> true));
    }

    @Test
    void preservesLookupFailure() {
        CompletableFuture<String> lookup = new CompletableFuture<>();
        IllegalStateException cause = new IllegalStateException("fixture");
        lookup.completeExceptionally(cause);
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> GitHubTask.awaitWhileRunning(lookup, () -> true));
        assertSame(cause, failure.getCause());
    }

    @Test
    void cancelsLookupWhenAlreadyDisabled() {
        CompletableFuture<String> lookup = new CompletableFuture<>();
        assertThrows(CancellationException.class,
                () -> GitHubTask.awaitWhileRunning(lookup, () -> false));
        assertTrue(lookup.isCancelled());
    }

    @Test
    void releasesWorkerAfterDisableWithoutCompletingLookup() throws Exception {
        CompletableFuture<String> lookup = new CompletableFuture<>();
        AtomicBoolean enabled = new AtomicBoolean(true);
        CountDownLatch waiting = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> worker = executor.submit(() -> assertThrows(CancellationException.class,
                    () -> GitHubTask.awaitWhileRunning(lookup, () -> {
                        waiting.countDown();
                        return enabled.get();
                    })));
            assertTrue(waiting.await(2, TimeUnit.SECONDS));
            enabled.set(false);
            worker.get(2, TimeUnit.SECONDS);
            assertTrue(lookup.isCancelled());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void preservesInterruptionAndCancelsPendingLookup() {
        CompletableFuture<String> lookup = new CompletableFuture<>();
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class,
                    () -> GitHubTask.awaitWhileRunning(lookup, () -> true));
            assertTrue(lookup.isCancelled());
        } finally {
            Thread.interrupted();
        }
    }
}
