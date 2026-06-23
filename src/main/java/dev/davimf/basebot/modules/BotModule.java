package dev.davimf.basebot.modules;

import dev.davimf.basebot.core.BotContext;

/**
 * A self-contained feature area from BOTSPECS (Base, Tickets, Sales, Facs).
 *
 * <p>Each module wires its own commands, component handlers and listeners during
 * startup via {@link #register}. Keeping modules behind one interface means the
 * application bootstrap simply iterates a list, and a module can be toggled off by
 * not adding it.
 */
public interface BotModule {

    /** Human-readable module name (for startup logging). */
    String name();

    /** Contributes commands/components/listeners. Called once, before JDA is built. */
    void register(ModuleRegistry registry, BotContext ctx);

    /**
     * Optional hook invoked after the gateway is ready — use for scheduler setup
     * (ADV/Orçamento expirations, panel refreshes) that needs a live {@link BotContext#jda()}.
     */
    default void onReady(BotContext ctx) {}
}
