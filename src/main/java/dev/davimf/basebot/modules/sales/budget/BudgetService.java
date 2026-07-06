// [OUTLINE START]
// Package: dev.davimf.basebot.modules.sales.budget
// 
// Class: BudgetService
// 
// Constructors:
//   - `Constructor` : `public BudgetService(BotContext ctx, BudgetRepository budgets, CatalogRepository catalog, PixKeyRepository pixKeys)`
// 
// Methods:
//   - `Method` : `private static final Logger log = LoggerFactory. getLogger(BudgetService.class)`
//   - `Method` : `private static final Duration TTL = Duration. ofHours(24)`
//   - `Method` : `public BudgetRepository repository()`
//   - `Method` : `public Container builderView(Budget budget)`
//   - `Method` : `private Budget guardClient(ButtonInteractionEvent event, String budgetId)`
//   - `Method` : `private Container builderViewOrEmpty(Budget b)`
//   - `Method` : `private int accent(String guildId)`
//   - `Method` : `private static String guildOf(ButtonInteractionEvent event)`
//   - `Method` : `private static int parseQuantity(String s)`
//   - `Method` : `private static String value(ModalInteractionEvent event, String key)`
// 
// Fields:
//   - `Field` : `private final BotContext ctx`
//   - `Field` : `private final BudgetRepository budgets`
//   - `Field` : `private final CatalogRepository catalog`
//   - `Field` : `private final PixKeyRepository pixKeys`
// [OUTLINE END]



package dev.davimf.basebot.modules.sales.budget;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.Budget;
import dev.davimf.basebot.database.model.BudgetItem;
import dev.davimf.basebot.database.model.CatalogCategory;
import dev.davimf.basebot.database.model.CatalogProduct;
import dev.davimf.basebot.database.model.PixKey;
import dev.davimf.basebot.modules.sales.catalog.CatalogRepository;
import dev.davimf.basebot.modules.sales.pix.PixDispatch;
import dev.davimf.basebot.modules.sales.pix.PixKeyRepository;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Drives the budget (orçamento) lifecycle (BOTSPECS Module 3): the seller's ephemeral
 * builder (pick category → product → quantity), sending the client approval embed, the
 * client-restricted approve/reject buttons (approve auto-dispatches the seller's Pix
 * charge), and the 24h auto-cancel sweep run by the scheduler.
 */
public final class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);
    private static final Duration TTL = Duration.ofHours(24);

    private final BotContext ctx;
    private final BudgetRepository budgets;
    private final CatalogRepository catalog;
    private final PixKeyRepository pixKeys;

    public BudgetService(BotContext ctx, BudgetRepository budgets,
                         CatalogRepository catalog, PixKeyRepository pixKeys) {
        this.ctx = ctx;
        this.budgets = budgets;
        this.catalog = catalog;
        this.pixKeys = pixKeys;
    }

    public BudgetRepository repository() {
        return budgets;
    }

    /** Builds the seller's draft builder view for a freshly created budget. */
    public Container builderView(Budget budget) {
        return BudgetView.builder(accent(budget.guildId()), budget.id(), budget.clientId(),
                budgets.listItems(budget.id()), catalog.listCategories(budget.guildId()));
    }

    // --- builder interactions --------------------------------------------------

    public void pickCategory(StringSelectInteractionEvent event, String budgetId, String categoryId) {
        Budget b = budgets.find(budgetId).orElse(null);
        CatalogCategory cat = catalog.findCategory(categoryId).orElse(null);
        if (b == null || cat == null) {
            edit(event, builderViewOrEmpty(b));
            return;
        }
        edit(event, BudgetView.productPicker(accent(b.guildId()), budgetId, cat,
                catalog.listProducts(categoryId)));
    }

    public void pickProduct(StringSelectInteractionEvent event, String budgetId, String productId) {
        CatalogProduct p = catalog.findProduct(productId).orElse(null);
        if (p == null) {
            Replies.ephemeral(event, ctx, "Esse produto não existe mais.");
            return;
        }
        event.replyModal(BudgetView.quantityModal(budgetId, productId, p.name())).queue();
    }

    public void addItem(ModalInteractionEvent event, String budgetId, String productId) {
        Budget b = budgets.find(budgetId).orElse(null);
        CatalogProduct p = catalog.findProduct(productId).orElse(null);
        if (b == null || p == null) {
            Replies.ephemeral(event, ctx, "Orçamento ou produto indisponível.");
            return;
        }
        int qty = parseQuantity(value(event, "qtd"));
        if (qty <= 0) {
            Replies.ephemeral(event, ctx, "Quantidade inválida.");
            return;
        }
        budgets.addItem(budgetId, p.name(), p.priceCents(), qty);
        edit(event, builderView(b));
    }

    public void back(ButtonInteractionEvent event, String budgetId) {
        edit(event, builderViewOrEmpty(budgets.find(budgetId).orElse(null)));
    }

    public void cancel(ButtonInteractionEvent event, String budgetId) {
        budgets.find(budgetId).ifPresent(b -> budgets.setStatus(budgetId, Budget.CANCELLED));
        event.editComponents(BudgetView.resolved(accent(guildOf(event)), "", "",
                List.of(), Emojis.of(Emojis.CHECK_NO, "❌") + " Orçamento cancelado.")).useComponentsV2().queue();
    }

    // --- send + approval -------------------------------------------------------

    public void send(ButtonInteractionEvent event, String budgetId) {
        Budget b = budgets.find(budgetId).orElse(null);
        if (b == null || event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Orçamento não encontrado.");
            return;
        }
        List<BudgetItem> items = budgets.listItems(budgetId);
        if (items.isEmpty()) {
            Replies.ephemeral(event, ctx, "Adicione ao menos um item antes de enviar.");
            return;
        }
        int accent = accent(b.guildId());
        Container approval = BudgetView.approval(accent, budgetId, b.sellerId(), b.clientId(), items);
        // Ack + collapse the ephemeral builder, then post the public approval embed.
        event.editComponents(BudgetView.resolved(accent, b.sellerId(), b.clientId(), items,
                "" + Emojis.of(Emojis.DM, "📨") + " Orçamento enviado ao cliente.")).useComponentsV2().queue();
        event.getChannel().sendMessageComponents(approval).useComponentsV2().queue(msg -> {
            budgets.markSent(budgetId, msg.getChannelId(), msg.getId(),
                    Instant.now().plus(TTL).toString());
            ctx.database().actionLogs().log(b.guildId(), b.sellerId(), b.clientId(),
                    "BUDGET_SENT", budgetId);
        });
    }

    public void approve(ButtonInteractionEvent event, String budgetId) {
        Budget b = guardClient(event, budgetId);
        if (b == null) {
            return;
        }
        List<BudgetItem> items = budgets.listItems(budgetId);
        Optional<PixKey> key = pixKeys.findByUser(b.guildId(), b.sellerId());
        budgets.setStatus(budgetId, Budget.APPROVED);
        ctx.database().actionLogs().log(b.guildId(), b.sellerId(), b.clientId(), "BUDGET_APPROVED", budgetId);

        int accent = accent(b.guildId());
        event.editComponents(BudgetView.resolved(accent, b.sellerId(), b.clientId(), items,
                "" + Emojis.of(Emojis.CHECK_YES, "✅") + " Aprovado por <@" + b.clientId() + ">.")).useComponentsV2().queue();

        if (key.isEmpty()) {
            event.getChannel().sendMessageComponents(Panels.container(accent, Panels.text("" + Emojis.of(Emojis.WARN, "⚠️") + " <@" + b.sellerId()
                            + "> registre sua chave com `/pix registrar` para enviar a cobrança.")))
                    .useComponentsV2().setAllowedMentions(EnumSet.of(Message.MentionType.USER)).queue();
            return;
        }
        PixDispatch.Rendered pix = PixDispatch.render(key.get(), BudgetView.total(items), accent, b.sellerId());
        event.getChannel().sendMessageComponents(pix.container()).useComponentsV2()
                .addFiles(pix.file()).queue();
    }

    public void reject(ButtonInteractionEvent event, String budgetId) {
        Budget b = guardClient(event, budgetId);
        if (b == null) {
            return;
        }
        List<BudgetItem> items = budgets.listItems(budgetId);
        budgets.setStatus(budgetId, Budget.REJECTED);
        ctx.database().actionLogs().log(b.guildId(), b.sellerId(), b.clientId(), "BUDGET_REJECTED", budgetId);
        event.editComponents(BudgetView.resolved(accent(b.guildId()), b.sellerId(), b.clientId(), items,
                "" + Emojis.of(Emojis.CHECK_NO, "❌") + " Recusado por <@" + b.clientId() + ">.")).useComponentsV2().queue();
        event.getChannel().sendMessageComponents(Panels.container(accent(b.guildId()),
                        Panels.text("<@" + b.sellerId() + "> seu orçamento foi recusado pelo cliente.")))
                .useComponentsV2().setAllowedMentions(EnumSet.of(Message.MentionType.USER)).queue();
    }

    // --- scheduler sweep -------------------------------------------------------

    /** Auto-cancels PENDING budgets older than 24h and notifies both parties (BOTSPECS §3). */
    public void sweepExpired(JDA jda) {
        List<Budget> expired = budgets.listPendingExpired(Instant.now().toString());
        for (Budget b : expired) {
            budgets.setStatus(b.id(), Budget.EXPIRED);
            ctx.database().actionLogs().log(b.guildId(), b.sellerId(), b.clientId(), "BUDGET_EXPIRED", b.id());
            if (b.channelId() == null) {
                continue;
            }
            TextChannel channel = jda.getTextChannelById(b.channelId());
            if (channel == null) {
                continue;
            }
            channel.sendMessageComponents(Panels.container(accent(b.guildId()),
                            Panels.text("" + Emojis.of(Emojis.HOURGLASS, "⏳") + " Orçamento de <@" + b.sellerId() + "> para <@" + b.clientId()
                                    + "> expirou após 24h sem resposta.")))
                    .useComponentsV2().setAllowedMentions(EnumSet.of(Message.MentionType.USER)).queue();
            if (b.messageId() != null) {
                List<BudgetItem> items = budgets.listItems(b.id());
                channel.retrieveMessageById(b.messageId()).flatMap(m ->
                                m.editMessageComponents(BudgetView.resolved(accent(b.guildId()),
                                        b.sellerId(), b.clientId(), items, Emojis.of(Emojis.HOURGLASS, "⌛") + " Expirado.")).useComponentsV2())
                        .queue(ok -> {}, err -> log.debug("Could not edit expired budget message: {}",
                                err.getMessage()));
            }
        }
        if (!expired.isEmpty()) {
            log.info("Expired {} pending budget(s).", expired.size());
        }
    }

    // --- helpers ---------------------------------------------------------------

    private Budget guardClient(ButtonInteractionEvent event, String budgetId) {
        Budget b = budgets.find(budgetId).orElse(null);
        if (b == null) {
            Replies.ephemeral(event, ctx, "Orçamento não encontrado.");
            return null;
        }
        if (!event.getUser().getId().equals(b.clientId())) {
            Replies.ephemeral(event, ctx,
                    "Apenas o cliente <@" + b.clientId() + "> pode responder a este orçamento.");
            return null;
        }
        if (!Budget.PENDING.equals(b.status())) {
            Replies.ephemeral(event, ctx, "Este orçamento já foi respondido.");
            return null;
        }
        return b;
    }

    private Container builderViewOrEmpty(Budget b) {
        return b == null
                ? BudgetView.resolved(accent("0"), "", "", List.of(), "Orçamento indisponível.")
                : builderView(b);
    }

    private void edit(IMessageEditCallback event, Container container) {
        event.editComponents(container).useComponentsV2().queue();
    }

    private int accent(String guildId) {
        return EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
    }

    private static String guildOf(ButtonInteractionEvent event) {
        return event.getGuild() == null ? "0" : event.getGuild().getId();
    }

    private static int parseQuantity(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String value(ModalInteractionEvent event, String key) {
        ModalMapping m = event.getValue(key);
        return m == null ? null : m.getAsString();
    }
}
