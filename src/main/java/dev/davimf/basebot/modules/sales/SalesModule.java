package dev.davimf.basebot.modules.sales;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.BotModule;
import dev.davimf.basebot.modules.ModuleRegistry;
import dev.davimf.basebot.modules.sales.budget.BudgetCommand;
import dev.davimf.basebot.modules.sales.budget.BudgetComponentHandler;
import dev.davimf.basebot.modules.sales.budget.BudgetRepository;
import dev.davimf.basebot.modules.sales.budget.BudgetService;
import dev.davimf.basebot.modules.sales.catalog.CatalogRepository;
import dev.davimf.basebot.modules.sales.catalog.TabelaCommand;
import dev.davimf.basebot.modules.sales.catalog.TabelaComponentHandler;
import dev.davimf.basebot.modules.sales.pix.PixCommand;
import dev.davimf.basebot.modules.sales.pix.PixComponentHandler;
import dev.davimf.basebot.modules.sales.pix.PixKeyRepository;

import java.util.concurrent.TimeUnit;

/**
 * Module 3 — Sales / Vendas (BOTSPECS §Module 3).
 *
 * <p>Pix payments ({@code /pix} + ZXing BR Code/QR generation), the product catalog
 * ({@code /tabela}, paginated) and budgets ({@code /orçamento} — interactive builder,
 * client approval that auto-dispatches the Pix charge, and a 24h auto-cancel sweep on
 * the scheduler).
 */
public final class SalesModule implements BotModule {

    private BudgetService budgetService;

    @Override
    public String name() {
        return "Sales";
    }

    @Override
    public void register(ModuleRegistry registry, BotContext ctx) {
        PixKeyRepository pixKeys = new PixKeyRepository(ctx.database().sqlite());
        registry.command(new PixCommand(pixKeys));
        registry.component(new PixComponentHandler());

        // Product catalog (BOTSPECS Module 3) — categories -> products, paginated.
        CatalogRepository catalog = new CatalogRepository(ctx.database().sqlite());
        registry.command(new TabelaCommand(catalog));
        registry.component(new TabelaComponentHandler(catalog));

        // Budgets / Orçamentos — interactive builder, client approval, Pix auto-dispatch.
        BudgetRepository budgets = new BudgetRepository(ctx.database().sqlite());
        this.budgetService = new BudgetService(ctx, budgets, catalog, pixKeys);
        registry.command(new BudgetCommand(budgetService));
        registry.component(new BudgetComponentHandler(budgetService));
    }

    @Override
    public void onReady(BotContext ctx) {
        // 24h budget auto-cancel: sweep every 10 minutes (BOTSPECS §3 scheduler).
        if (budgetService != null) {
            ctx.scheduler().repeating(() -> budgetService.sweepExpired(ctx.jda()),
                    1, 10, TimeUnit.MINUTES);
        }
    }
}
