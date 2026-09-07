package dev.davimf.basebot.core.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central scheduler for the bot's timed work (BOTSPECS §3): ADV/Warn 20-day
 * expirations, Orçamento 24h auto-cancel, and recurring panel refreshes. Backed by a
 * {@link ScheduledExecutorService} started on {@code onReady()} and shared with the
 * {@link dev.davimf.basebot.ratelimit.Debouncer} / BatchThrottler.
 *
 * <p>Tasks are wrapped so an exception in one run is logged and never kills the
 * underlying scheduled task (a raw {@code scheduleAtFixedRate} would silently stop).
 */
public final class TaskScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);

    private final ScheduledExecutorService executor;

    public TaskScheduler(int poolSize) {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "basebot-scheduler-" + n.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
        this.executor = Executors.newScheduledThreadPool(poolSize, factory);
    }

    /** The raw executor, for components that need it (Debouncer, BatchThrottler). */
    public ScheduledExecutorService executor() {
        return executor;
    }

    /** Runs {@code task} once after {@code delay}. */
    public void once(Runnable task, long delay, TimeUnit unit) {
        executor.schedule(guarded("once", task), delay, unit);
    }

    /** Runs {@code task} repeatedly at a fixed period, surviving individual failures. */
    public void repeating(Runnable task, long initialDelay, long period, TimeUnit unit) {
        executor.scheduleAtFixedRate(guarded("repeating", task), initialDelay, period, unit);
    }

    private Runnable guarded(String label, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable t) {
                log.error("Scheduled task ({}) threw an exception", label, t);
            }
        };
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
