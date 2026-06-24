package dev.davimf.basebot.modules.facs;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.BotModule;
import dev.davimf.basebot.modules.ModuleRegistry;
import dev.davimf.basebot.modules.facs.commands.HierarquiaCommand;
import dev.davimf.basebot.modules.facs.commands.PdCommand;
import dev.davimf.basebot.modules.facs.commands.PunicoesCommand;
import dev.davimf.basebot.modules.facs.commands.PunirCommand;
import dev.davimf.basebot.modules.facs.hierarchy.HierarchyService;
import dev.davimf.basebot.modules.facs.listeners.HierarchyListener;
import dev.davimf.basebot.modules.facs.punish.PunishComponentHandler;
import dev.davimf.basebot.modules.facs.punish.PunishService;
import dev.davimf.basebot.modules.facs.punish.PunishmentRepository;

import java.util.concurrent.TimeUnit;

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

    private PunishService punishService;

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

        // Punishments: /punir (Blacklist/Rebaixamento/ADV) + /punições history & revoke.
        PunishmentRepository punishments = new PunishmentRepository(ctx.database().sqlite());
        this.punishService = new PunishService(ctx, punishments);
        registry.command(new PunirCommand(punishService));
        registry.command(new PunicoesCommand(punishService));
        registry.component(new PunishComponentHandler(punishService));

        // TODO(Module 4): /solicitar-cargo, /produzir, /painel-financeiro,
        // /painel-acoes, /relatorio, /farm, plus the Actions/Reservations priority
        // queue and recruitment Set pipeline.
    }

    @Override
    public void onReady(BotContext ctx) {
        // 20-day ADV expiry: sweep once on boot, then hourly (BOTSPECS §4).
        if (punishService != null) {
            ctx.scheduler().repeating(punishService::sweepExpiredAdvs, 0, 1, TimeUnit.HOURS);
        }
    }
}
