package dev.davimf.basebot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Reads Pterodactyl stop/restart commands from stdin. */
public final class ConsoleControl {

    private static final Logger log = LoggerFactory.getLogger(ConsoleControl.class);
    private static final Set<String> SHUTDOWN_COMMANDS = Set.of(
            "stop", "shutdown", "exit", "restart", "reiniciar", "desligar");

    private ConsoleControl() {}

    public static void start(GracefulShutdown shutdown) {
        Thread thread = new Thread(() -> readLoop(shutdown), "basebot-console");
        thread.setDaemon(true);
        thread.start();
        log.info("Console control ready (stop/shutdown/restart).");
    }

    static boolean isShutdownCommand(String input) {
        return input != null && SHUTDOWN_COMMANDS.contains(input.trim().toLowerCase(Locale.ROOT));
    }

    private static void readLoop(GracefulShutdown shutdown) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isShutdownCommand(line)) {
                    log.info("Received console command '{}'.", line.trim());
                    shutdown.run();
                    System.exit(0);
                    return;
                }
                if (!line.isBlank()) log.warn("Unknown console command '{}'.", line.trim());
            }
            log.info("Console input closed; bot will keep running.");
        } catch (IOException e) {
            log.error("Console control stopped after an I/O error.", e);
        }
    }
}
