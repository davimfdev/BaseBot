package dev.davimf.basebot;

import dev.davimf.basebot.core.scheduler.TaskScheduler;
import dev.davimf.basebot.database.DatabaseManager;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/** Idempotent shutdown shared by console commands and the JVM shutdown hook. */
public final class GracefulShutdown implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdown.class);
    private static final Duration JDA_TIMEOUT = Duration.ofSeconds(10);

    private final AtomicBoolean started = new AtomicBoolean();
    private final Runnable stopScheduler;
    private final Runnable stopJda;
    private final Runnable closeDatabase;

    public GracefulShutdown(TaskScheduler scheduler, JDA jda, DatabaseManager database) {
        this(scheduler::close, () -> stopJda(jda), database::close);
    }

    GracefulShutdown(Runnable stopScheduler, Runnable stopJda, Runnable closeDatabase) {
        this.stopScheduler = stopScheduler;
        this.stopJda = stopJda;
        this.closeDatabase = closeDatabase;
    }

    @Override
    public void run() {
        if (!started.compareAndSet(false, true)) {
            log.info("Shutdown already in progress; ignoring duplicate request.");
            return;
        }
        log.info("Graceful shutdown started.");
        runStep("scheduler", stopScheduler);
        runStep("Discord connection", stopJda);
        runStep("databases", closeDatabase);
        log.info("Graceful shutdown completed.");
    }

    private static void stopJda(JDA jda) {
        jda.shutdown();
        try {
            if (!jda.awaitShutdown(JDA_TIMEOUT)) {
                log.warn("JDA did not stop within {}; forcing shutdown.", JDA_TIMEOUT);
                jda.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for JDA; forcing shutdown.");
            jda.shutdownNow();
        }
    }

    private static void runStep(String name, Runnable step) {
        try {
            log.info("Stopping {}...", name);
            step.run();
            log.info("Stopped {}.", name);
        } catch (RuntimeException e) {
            log.error("Failed while stopping {}; continuing shutdown.", name, e);
        }
    }
}
