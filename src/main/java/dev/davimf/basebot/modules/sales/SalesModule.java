package dev.davimf.basebot.modules.sales;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.BotModule;
import dev.davimf.basebot.modules.ModuleRegistry;
import dev.davimf.basebot.modules.sales.catalog.CatalogRepository;
import dev.davimf.basebot.modules.sales.catalog.TabelaCommand;
import dev.davimf.basebot.modules.sales.catalog.TabelaComponentHandler;
import dev.davimf.basebot.modules.sales.pix.PixCommand;
import dev.davimf.basebot.modules.sales.pix.PixComponentHandler;
import dev.davimf.basebot.modules.sales.pix.PixKeyRepository;

/**
 * Module 3 — Sales / Vendas (BOTSPECS §Module 3).
 *
 * <p>Pix payments ({@code /pix} + ZXing BR Code/QR generation), Budgets
 * ({@code /orçamento} with 24h auto-cancel via the scheduler) and the product catalog
 * ({@code /tabela} with paginated embeds). Scaffolded; commands are TODOs.
 */
public final class SalesModule implements BotModule {

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

        // TODO(Module 3): /orçamento (selector + approval embed + auto-dispatch Pix).
        // Budget expiry uses ctx.scheduler() to auto-cancel rows past `expires_at`.
    }

    @Override
    public void onReady(BotContext ctx) {
        // TODO: schedule the 24h budget auto-cancel sweep here (ctx.scheduler().repeating(...)).
    }
}
