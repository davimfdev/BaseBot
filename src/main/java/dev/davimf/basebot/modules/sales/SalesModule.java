package dev.davimf.basebot.modules.sales;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.modules.BotModule;
import dev.davimf.basebot.modules.ModuleRegistry;

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
        // TODO(Module 3): /pix (ZXing Pix payload + QR), /orçamento (selector + approval
        // embed + auto-dispatch Pix), /tabela (paginated catalog). Budget expiry uses
        // ctx.scheduler() to auto-cancel rows past `expires_at` in the `budgets` table.
    }

    @Override
    public void onReady(BotContext ctx) {
        // TODO: schedule the 24h budget auto-cancel sweep here (ctx.scheduler().repeating(...)).
    }
}
