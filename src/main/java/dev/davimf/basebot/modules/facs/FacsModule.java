package dev.davimf.basebot.modules.facs;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.BotModule;
import dev.davimf.basebot.modules.ModuleRegistry;
import dev.davimf.basebot.modules.facs.actions.ActionComponentHandler;
import dev.davimf.basebot.modules.facs.actions.ActionRepository;
import dev.davimf.basebot.modules.facs.actions.ActionService;
import dev.davimf.basebot.modules.facs.commands.HierarquiaCommand;
import dev.davimf.basebot.modules.facs.commands.PainelAcoesCommand;
import dev.davimf.basebot.modules.facs.commands.PdCommand;
import dev.davimf.basebot.modules.facs.commands.PunicoesCommand;
import dev.davimf.basebot.modules.facs.commands.PunirCommand;
import dev.davimf.basebot.modules.facs.hierarchy.HierarchyService;
import dev.davimf.basebot.modules.facs.listeners.HierarchyListener;
import dev.davimf.basebot.modules.facs.commands.FarmCommand;
import dev.davimf.basebot.modules.facs.commands.PainelFinanceiroCommand;
import dev.davimf.basebot.modules.facs.commands.ProduzirCommand;
import dev.davimf.basebot.modules.facs.commands.RecrutamentoCommand;
import dev.davimf.basebot.modules.facs.commands.RelatorioCommand;
import dev.davimf.basebot.modules.facs.commands.SolicitarCargoCommand;
import dev.davimf.basebot.modules.facs.recruit.RecruitComponentHandler;
import dev.davimf.basebot.modules.facs.recruit.RecruitRequestRepository;
import dev.davimf.basebot.modules.facs.recruit.RecruitStatsRepository;
import dev.davimf.basebot.modules.facs.economy.EconomyRepository;
import dev.davimf.basebot.modules.facs.economy.RecipeRepository;
import dev.davimf.basebot.modules.facs.economy.FarmComponentHandler;
import dev.davimf.basebot.modules.facs.economy.FarmRepository;
import dev.davimf.basebot.modules.facs.economy.FarmService;
import dev.davimf.basebot.modules.facs.economy.FinanceComponentHandler;
import dev.davimf.basebot.modules.facs.economy.FinanceService;
import dev.davimf.basebot.modules.facs.punish.PunishComponentHandler;
import dev.davimf.basebot.modules.facs.punish.PunishService;
import dev.davimf.basebot.modules.facs.punish.PunishmentRepository;
import dev.davimf.basebot.modules.facs.sets.SetRequestComponentHandler;

import java.util.concurrent.TimeUnit;

/**
 * Module 4 — Facs / FiveM roleplay management (BOTSPECS §Module 4).
 *
 * <p>Hierarchy-aware commands ({@code /pd}, {@code /solicitar-cargo}, {@code /produzir},
 * {@code /painel-financeiro}, {@code /painel-acoes}, {@code /relatorio}, {@code /hierarquia},
 * {@code /punir}, {@code /punições}, {@code /farm}, {@code /recrutamento}), the
 * Actions/Reservations Elite-priority queue, the recruitment/Set pipeline, and
 * punishments (ADV 20-day expiry). The Hierarchy panel auto-updates on role changes
 * (5s debounce); ADV expiry runs on the scheduler.
 */
public final class FacsModule implements BotModule {

    private PunishService punishService;
    private ActionService actionService;

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

        // Set pipeline: /solicitar-cargo posts a request; managers approve/reject.
        registry.command(new SolicitarCargoCommand());
        registry.component(new SetRequestComponentHandler());

        // Economy: /painel-financeiro treasury panel (deposit/withdraw/transfer + toggles).
        EconomyRepository economy = new EconomyRepository(ctx.database().sqlite());
        FinanceService finance = new FinanceService(ctx, economy);
        registry.command(new PainelFinanceiroCommand(finance));
        registry.component(new FinanceComponentHandler(finance));

        // Farm: /farm submits materials; managers approve -> stock + treasury payout.
        FarmService farm = new FarmService(ctx, economy, new FarmRepository(ctx.database().sqlite()));
        registry.command(new FarmCommand());
        registry.component(new FarmComponentHandler(farm));

        // Production: /produzir defines recipes + crafts products from stock.
        registry.command(new ProduzirCommand(economy, new RecipeRepository(ctx.database().sqlite())));

        // Reports: /relatorio (financeiro|acoes) as embed (<=7d) or CSV (unlimited).
        registry.command(new RelatorioCommand(economy));

        // Recruitment: /recrutamento painel -> apply modal -> manager Accept grants the
        // entry role and credits the recruiter (+1 in recruiter_stats).
        registry.command(new RecrutamentoCommand());
        registry.component(new RecruitComponentHandler(
                new RecruitStatsRepository(ctx.database().sqlite()),
                new RecruitRequestRepository(ctx.database().sqlite())));

        // Actions/Reservations: /painel-acoes with the Elite-priority queue, Alinhamento
        // pings, backfill and Vitória/Derrota controls.
        this.actionService = new ActionService(ctx,
                new ActionRepository(ctx.database().sqlite()), economy);
        registry.command(new PainelAcoesCommand(actionService));
        registry.component(new ActionComponentHandler(actionService));
    }

    @Override
    public void onReady(BotContext ctx) {
        // 20-day ADV expiry: sweep once on boot, then hourly (BOTSPECS §4).
        if (punishService != null) {
            ctx.scheduler().repeating(punishService::sweepExpiredAdvs, 0, 1, TimeUnit.HOURS);
        }
        // Scheduled actions: reveal Vitória/Derrota once their time arrives (every minute).
        if (actionService != null) {
            ctx.scheduler().repeating(actionService::sweepDueActions, 1, 1, TimeUnit.MINUTES);
        }
    }
}
