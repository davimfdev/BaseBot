// [OUTLINE START]
// Package: dev.davimf.basebot
// 
// Class: BaseBot
// 
// Constructors:
//   - `Constructor` : `private BaseBot()`
// 
// Methods:
//   - `Method` : `private static final Logger log = LoggerFactory. getLogger(BaseBot.class)`
// [OUTLINE END]



package dev.davimf.basebot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint. Delegates to {@link BotApplication}; kept tiny so the bootstrap
 * logic is testable and the manifest Main-Class is stable.
 */
public final class BaseBot {

    private static final Logger log = LoggerFactory.getLogger(BaseBot.class);

    private BaseBot() {}

    public static void main(String[] args) {
        try {
            new BotApplication().start();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Startup interrupted", e);
            System.exit(1);
        } catch (Exception e) {
            log.error("Fatal error during startup", e);
            System.exit(1);
        }
    }
}
