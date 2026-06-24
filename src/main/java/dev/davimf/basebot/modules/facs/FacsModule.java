package dev.davimf.basebot.modules.facs;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.BotModule;
import dev.davimf.basebot.modules.ModuleRegistry;
import dev.davimf.basebot.modules.facs.commands.HierarquiaCommand;
import dev.davimf.basebot.modules.facs.commands.PdCommand;
import dev.davimf.basebot.modules.facs.hierarchy.HierarchyService;
import dev.davimf.basebot.modules.facs.listeners.HierarchyListener;

/**
 * Module 4 — Facs / FiveM roleplay management (BOTSPECS §Module 4).
 *
 * <p>Hierarchy-aware commands ({@code /pd}, {@code /solicitar-cargo}, {@code /produzir},
 * panels, {@code /relatorio}, {@code /hierarquia}), the Actions/Reservations system with
 * its priority queue, recruitment pipeline, and punishments/farm (ADV 20-day expiry).
 * The auto-updating Hierarchy panel listener (with 5s debounce) is wired as the
 * reference; the rest are TODOs.
 */
public final class FacsModule implements BotModule {

    @Override
    public String name() {
        return "Facs";
    }

    @Override
    public void register(ModuleRegistry registry, BotContext ctx) {
        // /hierarquia: auto-updating chain-of-command panel + the role-change listener
        // that refreshes it (5s debounce so a burst triggers one edit; BOTSPECS §1, §4).
        HierarchyService hierarchy = new HierarchyService(ctx);
        registry.command(new HierarquiaCommand(hierarchy));
        registry.listener(new HierarchyListener(ctx, hierarchy));

        // Disciplinary: /pd removes a member + logs to PD and Punishments channels.
        registry.command(new PdCommand());

        // TODO(Module 4): /solicitar-cargo, /produzir, /painel-financeiro,
        // /painel-acoes, /relatorio, /punir, /punições, /farm, plus the
        // Actions/Reservations priority queue and recruitment Set pipeline.
    }

    @Override
    public void onReady(BotContext ctx) {
        // TODO: schedule the 20-day ADV/Warn expiry sweep (DB check on boot + repeating).
    }
}
