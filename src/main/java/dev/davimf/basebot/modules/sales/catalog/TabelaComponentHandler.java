// [OUTLINE START]
// Package: dev.davimf.basebot.modules.sales.catalog
// 
// Class: TabelaComponentHandler
// 
// Constructors:
//   - `Constructor` : `public TabelaComponentHandler(CatalogRepository catalog)`
// 
// Methods:
//   - `Method` : `public String namespace()`
//   - `Method` : `private Container hub(BotContext ctx, String guildId)`
//   - `Method` : `private int accent(BotContext ctx, String guildId)`
//   - `Method` : `private static String value(ModalInteractionEvent event, String key)`
//   - `Method` : `private static int parseInt(String s)`
// 
// Fields:
//   - `Field` : `private final CatalogRepository catalog`
// [OUTLINE END]



package dev.davimf.basebot.modules.sales.catalog;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.CatalogCategory;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Money;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

import java.util.OptionalLong;

/** Routes the {@code /tabela} catalog hub interactions (BOTSPECS Module 3). */
public final class TabelaComponentHandler implements ComponentHandler {

    private final CatalogRepository catalog;

    public TabelaComponentHandler(CatalogRepository catalog) {
        this.catalog = catalog;
    }

    @Override
    public String namespace() {
        return TabelaView.NS;
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        switch (id.action()) {
            case "catview" -> showCategory(event, ctx, event.getValues().get(0), 0);
            case "proddel" -> {
                catalog.deleteProduct(event.getValues().get(0));
                showCategory(event, ctx, id.arg(0), 0);
            }
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        switch (id.action()) {
            case "hub" -> edit(event, hub(ctx, event.getGuild().getId()));
            case "catnew" -> event.replyModal(TabelaView.categoryModal()).queue();
            case "catdel" -> {
                catalog.deleteCategory(id.arg(0));
                edit(event, hub(ctx, event.getGuild().getId()));
            }
            case "prodnav" -> showCategory(event, ctx, id.arg(0), parseInt(id.arg(1)));
            case "prodnew" -> event.replyModal(TabelaView.productModal(id.arg(0))).queue();
            default -> { /* not ours */ }
        }
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        if (event.getGuild() == null) {
            return;
        }
        String guildId = event.getGuild().getId();
        switch (id.action()) {
            case "catform" -> {
                String name = value(event, "nome");
                if (name != null && !name.isBlank()) {
                    catalog.createCategory(guildId, name.trim());
                }
                edit(event, hub(ctx, guildId));
            }
            case "prodform" -> {
                String categoryId = id.arg(0);
                String name = value(event, "nome");
                OptionalLong price = Money.parse(value(event, "preco"));
                if (price.isEmpty()) {
                    Replies.ephemeral(event, ctx, "Preço inválido. Use algo como `1.500,00`.");
                    return;
                }
                if (name != null && !name.isBlank()) {
                    catalog.createProduct(guildId, categoryId, name.trim(),
                            value(event, "descricao"), price.getAsLong());
                }
                showCategory(event, ctx, categoryId, 0);
            }
            default -> { /* not ours */ }
        }
    }

    // --- helpers ---------------------------------------------------------------

    private void showCategory(IMessageEditCallback event, BotContext ctx, String categoryId, int page) {
        CatalogCategory cat = catalog.findCategory(categoryId).orElse(null);
        if (cat == null) {
            // Category vanished — fall back to the hub.
            if (event instanceof StringSelectInteractionEvent s && s.getGuild() != null) {
                edit(event, hub(ctx, s.getGuild().getId()));
            }
            return;
        }
        edit(event, TabelaView.category(accent(ctx, cat.guildId()), cat,
                catalog.listProducts(categoryId), page));
    }

    private Container hub(BotContext ctx, String guildId) {
        return TabelaView.hub(accent(ctx, guildId), catalog.listCategories(guildId));
    }

    private void edit(IMessageEditCallback event, Container container) {
        event.editComponents(container).useComponentsV2().queue();
    }

    private int accent(BotContext ctx, String guildId) {
        return EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
    }

    private static String value(ModalInteractionEvent event, String key) {
        ModalMapping m = event.getValue(key);
        return m == null ? null : m.getAsString();
    }

    private static int parseInt(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
