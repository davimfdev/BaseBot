// [OUTLINE START]
// Package: dev.davimf.basebot.ratelimit
// 
// Class: Debouncer
// 
// Constructors:
//   - `Constructor` : `public Debouncer(ScheduledExecutorService scheduler, long delayMillis)`
// 
// Fields:
//   - `Field` : `private final ScheduledExecutorService scheduler`
//   - `Field` : `private final long delayMillis`
//   - `Field` : `private final ConcurrentHashMap<String, ScheduledFuture<?>> pending`
// [OUTLINE END]



package dev.davimf.basebot.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Per-key debouncer. Coalesces bursts of events into a single trailing-edge action.
 *
 * <p>Primary use (BOTSPECS §1): when {@code GuildMemberRoleAdd/RemoveEvent} fires
 * rapidly, schedule the Hierarchy-panel refresh keyed by guild id; only the last call
 * within the window actually runs, preventing Discord rate-limit hits.
 *
 * <p>Thread-safe. Each {@link #debounce(String, Runnable)} cancels any pending action
 * for the same key and reschedules it {@code delayMillis} into the future.
 */
public final class Debouncer {

    private final ScheduledExecutorService scheduler;
    private final long delayMillis;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public Debouncer(ScheduledExecutorService scheduler, long delayMillis) {
        this.scheduler = scheduler;
        this.delayMillis = delayMillis;
    }

    /** Schedules {@code action} for {@code key}, replacing any not-yet-fired action. */
    public void debounce(String key, Runnable action) {
        ScheduledFuture<?> next = scheduler.schedule(() -> {
            pending.remove(key);
            action.run();
        }, delayMillis, TimeUnit.MILLISECONDS);

        ScheduledFuture<?> prev = pending.put(key, next);
        if (prev != null) {
            prev.cancel(false);
        }
    }

    /** Cancels a pending action for {@code key}, if any. */
    public void cancel(String key) {
        ScheduledFuture<?> f = pending.remove(key);
        if (f != null) {
            f.cancel(false);
        }
    }
}
