// [OUTLINE START]
// Package: dev.davimf.basebot.ratelimit
// 
// Class: BatchThrottler
// 
// Constructors:
//   - `Constructor` : `public BatchThrottler(ScheduledExecutorService scheduler, int batchSize, long interval, TimeUnit unit)`
// 
// Methods:
//   - `Method` : `public <T> CompletableFuture<Void> run(List<T> items, Consumer<List<T>> batchConsumer)`
//   - `Method` : `private <T> List<List<T>> partition(List<T> items)`
// 
// Fields:
//   - `Field` : `private final ScheduledExecutorService scheduler`
//   - `Field` : `private final int batchSize`
//   - `Field` : `private final long intervalMillis`
// [OUTLINE END]



package dev.davimf.basebot.ratelimit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Emits a list of items in throttled batches to dodge Discord anti-spam flags.
 *
 * <p>BOTSPECS §2: ghost pings ({@code /listacargo}) must be sent in batches of 5 every
 * 30 seconds. Generalized here: {@code batchSize} items per tick, {@code interval}
 * between ticks. The first batch fires immediately; the rest are scheduled.
 */
public final class BatchThrottler {

    private final ScheduledExecutorService scheduler;
    private final int batchSize;
    private final long intervalMillis;

    public BatchThrottler(ScheduledExecutorService scheduler, int batchSize, long interval, TimeUnit unit) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        this.scheduler = scheduler;
        this.batchSize = batchSize;
        this.intervalMillis = unit.toMillis(interval);
    }

    /**
     * Splits {@code items} into batches and feeds each batch to {@code batchConsumer}
     * on the scheduled cadence. Completes when the final batch has been dispatched.
     */
    public <T> CompletableFuture<Void> run(List<T> items, Consumer<List<T>> batchConsumer) {
        List<List<T>> batches = partition(items);
        CompletableFuture<Void> done = new CompletableFuture<>();
        dispatch(batches, 0, batchConsumer, done);
        return done;
    }

    private <T> void dispatch(List<List<T>> batches, int index,
                              Consumer<List<T>> consumer, CompletableFuture<Void> done) {
        if (index >= batches.size()) {
            done.complete(null);
            return;
        }
        try {
            consumer.accept(batches.get(index));
        } catch (RuntimeException e) {
            done.completeExceptionally(e);
            return;
        }
        if (index + 1 >= batches.size()) {
            done.complete(null);
            return;
        }
        scheduler.schedule(
                () -> dispatch(batches, index + 1, consumer, done),
                intervalMillis, TimeUnit.MILLISECONDS);
    }

    private <T> List<List<T>> partition(List<T> items) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            out.add(new ArrayList<>(items.subList(i, Math.min(i + batchSize, items.size()))));
        }
        return out;
    }
}
