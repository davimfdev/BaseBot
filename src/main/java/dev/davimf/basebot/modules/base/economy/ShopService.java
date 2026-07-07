package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.Durations;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;
import java.util.List;

/** Loja da economia: catálogo, saga de compra (reservar→debitar→entregar→compensar) e sweep de expiração. */
public final class ShopService {

    private final BotContext ctx;
    private final ShopItemRepository items;
    private final ShopStockRepository stock;
    private final ShopPurchaseRepository purchases;
    private final WalletRepository wallets;

    public ShopService(BotContext ctx) {
        this.ctx = ctx;
        this.items = new ShopItemRepository(ctx.database().postgres());
        this.stock = new ShopStockRepository(ctx.database().sqlite());
        this.purchases = new ShopPurchaseRepository(ctx.database().sqlite());
        this.wallets = new WalletRepository(ctx.database().sqlite());
    }

    public ShopItemRepository items() { return items; }

    private GuildConfig cfg(Guild g) { return ctx.database().guildConfig().findOrEmpty(g.getId()); }

    /** Catálogo ativo com o contador {@code sold} preenchido a partir do shop_stock (SQLite). */
    public List<ShopItem> catalog(Guild g) {
        List<ShopItem> out = new ArrayList<>();
        for (ShopItem it : items.list(g.getId())) {
            out.add(it.withSold(stock.soldOf(it.id())));
        }
        return out;
    }

    public String buy(Guild g, Member m, long itemId) {
        GuildConfig cfg = cfg(g);
        if (!EconomyConfig.enabled(cfg)) {
            return "Economia desativada neste servidor.";
        }
        ShopItem item = items.find(g.getId(), itemId);
        if (item == null) {
            return "Item indisponível.";
        }
        String gid = g.getId();
        String uid = m.getId();
        long now = System.currentTimeMillis();

        // Ramo de renovação: cargo temporário ativo → estende (sem estoque nem per_user).
        if (item.type() == ShopItem.Type.ROLE_TEMP) {
            ShopPurchaseRepository.Purchase active = purchases.activeTemp(gid, uid, itemId, now);
            if (active != null) {
                Role role = item.roleId() == null ? null : g.getRoleById(item.roleId());
                if (role == null) {
                    return "Item mal configurado (cargo removido). Avise a administração.";
                }
                if (!wallets.tryDebitCash(gid, uid, item.price())) {
                    return "Saldo insuficiente na carteira.";
                }
                long newExp = ShopPurchaseRules.extendedExpiry(active.expiresAt(), item.durationS(), now);
                purchases.extend(active.id(), newExp);
                g.addRoleToMember(m, role).reason("Loja: renovação de " + item.name()).queue(null, err -> {});
                return Emojis.of(Emojis.CHECK_YES, "✅") + " Renovado **" + item.name() + "** por mais "
                        + Durations.format(item.durationS() * 1000L) + " (−" + EconomyFormat.formatNamed(item.price(), cfg) + ").";
            }
        }

        // Compra nova: validações prévias (sem debitar).
        if (item.type() == ShopItem.Type.ROLE_PERM && item.roleId() != null && m.getRoles().stream()
                .anyMatch(r -> r.getId().equals(item.roleId()))) {
            return "Você já tem esse cargo.";
        }
        int active = purchases.countActiveByUserItem(gid, uid, itemId, now);
        if (ShopPurchaseRules.perUserReached(item.perUser(), active)) {
            return "Você atingiu o limite de compras deste item.";
        }
        Role role = null;
        if (item.type() != ShopItem.Type.CUSTOM) {
            role = item.roleId() == null ? null : g.getRoleById(item.roleId());
            if (role == null) {
                return "Item mal configurado (cargo removido). Avise a administração.";
            }
            if (!g.getSelfMember().canInteract(role)) {
                return "Não consigo te dar esse cargo (hierarquia). Avise a administração.";
            }
        }

        // Reservar estoque → debitar → entregar.
        if (!stock.reserveStock(itemId, item.stock())) {
            return "Item esgotado.";
        }
        if (!wallets.tryDebitCash(gid, uid, item.price())) {
            stock.releaseStock(itemId);
            return "Saldo insuficiente na carteira.";
        }

        Long expires = item.type() == ShopItem.Type.ROLE_TEMP
                ? ShopPurchaseRules.newExpiry(item.durationS(), now) : null;
        long purchaseId = purchases.insert(new ShopPurchaseRepository.Purchase(
                0, gid, itemId, uid, role == null ? null : role.getId(), expires, item.price(), now));

        String priceMd = EconomyFormat.formatNamed(item.price(), cfg);
        if (item.type() == ShopItem.Type.CUSTOM) {
            ChannelLog.post(ctx, gid, "log-loja", "## " + Emojis.of(Emojis.SALES, "🛒") + " Compra na loja\n"
                    + "Comprador · " + m.getAsMention() + " `" + uid + "`\n"
                    + "Item · **" + item.name() + "** `#" + item.id() + "`\n"
                    + "Valor · " + priceMd + "\n"
                    + "-# Item custom — entregar manualmente.");
            return Emojis.of(Emojis.CHECK_YES, "✅") + " Compra registrada: **" + item.name() + "** (−" + priceMd
                    + "). A administração vai te entregar em breve.";
        }

        final Role granted = role;
        g.addRoleToMember(m, granted).reason("Loja: " + item.name()).queue(
                ok -> ChannelLog.post(ctx, gid, "log-loja", "## " + Emojis.of(Emojis.SALES, "🛒") + " Compra na loja\n"
                        + "Comprador · " + m.getAsMention() + "\nItem · **" + item.name() + "** " + granted.getAsMention()
                        + "\nValor · " + priceMd + (expires == null ? "" : "\nExpira · <t:" + (expires / 1000) + ":R>")),
                err -> refund(g, m, item, purchaseId, priceMd));

        String extra = item.type() == ShopItem.Type.ROLE_TEMP
                ? " por " + Durations.format(item.durationS() * 1000L) : "";
        return Emojis.of(Emojis.CHECK_YES, "✅") + " Comprado **" + item.name() + "**" + extra
                + " (−" + priceMd + ").";
    }

    /** Compensa uma entrega de cargo que falhou de forma assíncrona (raro, pós-canInteract). */
    private void refund(Guild g, Member m, ShopItem item, long purchaseId, String priceMd) {
        stock.releaseStock(item.id());
        wallets.addCash(g.getId(), m.getId(), item.price());
        purchases.claim(purchaseId);
        ChannelLog.post(ctx, g.getId(), "log-loja", "## " + Emojis.of(Emojis.WARN, "⚠️")
                + " Estorno automático\nComprador · " + m.getAsMention() + "\nItem · **" + item.name()
                + "**\nValor devolvido · " + priceMd + "\n-# Falha ao conceder o cargo (entrega desfeita).");
    }

    /** Remove cargos temporários vencidos (claim-by-delete evita remoção dupla). À prova de restart. */
    public void sweep() {
        for (ShopPurchaseRepository.Purchase p : purchases.due(System.currentTimeMillis())) {
            if (!purchases.claim(p.id())) {
                continue; // outro tick já pegou
            }
            if (p.roleId() == null) {
                continue;
            }
            Guild g = ctx.jda().getGuildById(p.guildId());
            if (g == null) {
                continue;
            }
            Role role = g.getRoleById(p.roleId());
            if (role == null) {
                continue;
            }
            g.removeRoleFromMember(net.dv8tion.jda.api.entities.UserSnowflake.fromId(p.userId()), role)
                    .reason("Loja: cargo temporário expirado").queue(null, err -> {});
        }
    }
}
