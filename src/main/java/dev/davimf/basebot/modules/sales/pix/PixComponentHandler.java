package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentHandler;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.database.model.PixKey;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Money;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;

import java.util.List;
import java.util.Optional;

/** Painel Pix (namespace "pix"): enviar cobrança + CRUD de chaves. */
public final class PixComponentHandler implements ComponentHandler {

    @Override
    public String namespace() {
        return "pix";
    }

    @Override
    public void onButton(ButtonInteractionEvent event, ComponentId id, BotContext ctx) {
        String guildId = event.getGuild() == null ? "0" : event.getGuild().getId();
        String userId = event.getUser().getId();
        PixKeyRepository repo = repo(ctx);
        int accent = accent(ctx, guildId);
        switch (id.action()) {
            case "confirmar" -> {
                if (!PixOwnership.isOwner(userId, id.arg(0))) {
                    Replies.ephemeral(event, ctx, "Apenas o dono do Pix pode confirmar este pagamento.");
                    return;
                }
                long cents = amountCentsArg(id.arg(1));
                String clientId = id.arg(2);
                event.editComponents(PixDispatch.confirmedContainer(accent, cents, userId, clientId))
                        .useComponentsV2().setReplace(true).queue();
                ChannelLog.post(ctx, guildId, "log-vendas", saleLog(userId, cents, clientId));
            }
            case "root" -> edit(event, PixPanelView.root(accent, repo.list(guildId, userId).size()));
            case "manage" -> edit(event, PixPanelView.manage(accent, repo.list(guildId, userId)));
            case "send" -> {
                List<PixKey> keys = repo.list(guildId, userId);
                if (keys.isEmpty()) {
                    Replies.ephemeral(event, ctx, "Cadastre uma chave antes de enviar uma cobrança.");
                    return;
                }
                edit(event, PixPanelView.sendPicker(accent, keys, ""));
            }
            case "new" -> edit(event, PixPanelView.newType(accent));
            case "edit" -> {
                Optional<PixKey> k = repo.find(Long.parseLong(id.arg(0)));
                if (k.isEmpty()) {
                    Replies.ephemeral(event, ctx, "Chave não encontrada.");
                    return;
                }
                event.replyModal(keyModal("editform", id.arg(0), k.get().keyValue(), k.get().merchantName())).queue();
            }
            case "del" -> {
                repo.delete(Long.parseLong(id.arg(0)));
                edit(event, PixPanelView.manage(accent, repo.list(guildId, userId)));
            }
            default -> { }
        }
    }

    @Override
    public void onStringSelect(StringSelectInteractionEvent event, ComponentId id, BotContext ctx) {
        switch (id.action()) {
            case "typenew" -> {
                String tipo = event.getValues().get(0);
                event.replyModal(keyModal("newform", tipo, "", "")).queue();
            }
            case "sendkey" -> {
                String clientId = id.arg(0) == null ? "" : id.arg(0);
                String keyId = event.getValues().get(0);
                TextInput valor = TextInput.create("valor", TextInputStyle.SHORT)
                        .setPlaceholder("Valor em R$ (opcional, ex.: 49.90)").setRequired(false).setMaxLength(15)
                        .build();
                event.replyModal(Modal.create(ComponentId.of("pix", "sendform", keyId, clientId), "Enviar cobrança")
                        .addComponents(Label.of("Valor", valor)).build()).queue();
            }
            default -> { }
        }
    }

    @Override
    public void onEntitySelect(EntitySelectInteractionEvent event, ComponentId id, BotContext ctx) {
        if (!"sendclient".equals(id.action())) {
            return;
        }
        String guildId = event.getGuild() == null ? "0" : event.getGuild().getId();
        String userId = event.getUser().getId();
        List<User> picked = event.getMentions().getUsers();
        String clientId = picked.isEmpty() ? "" : picked.get(0).getId();
        PixKeyRepository repo = repo(ctx);
        edit(event, PixPanelView.sendPicker(accent(ctx, guildId), repo.list(guildId, userId), clientId));
    }

    @Override
    public void onModal(ModalInteractionEvent event, ComponentId id, BotContext ctx) {
        String guildId = event.getGuild() == null ? "0" : event.getGuild().getId();
        String userId = event.getUser().getId();
        PixKeyRepository repo = repo(ctx);
        int accent = accent(ctx, guildId);
        switch (id.action()) {
            case "newform" -> {
                String tipo = id.arg(0);
                PixKeyNormalizer.Result r = PixKeyNormalizer.normalize(tipo, value(event, "chave"));
                if (!r.ok()) {
                    Replies.ephemeral(event, ctx, r.error());
                    return;
                }
                repo.insert(new PixKey(0, guildId, userId, tipo, r.value(), name(event)));
                edit(event, PixPanelView.manage(accent, repo.list(guildId, userId)));
            }
            case "editform" -> {
                Optional<PixKey> existing = repo.find(Long.parseLong(id.arg(0)));
                if (existing.isEmpty()) {
                    Replies.ephemeral(event, ctx, "Chave não encontrada.");
                    return;
                }
                PixKeyNormalizer.Result r = PixKeyNormalizer.normalize(existing.get().keyType(), value(event, "chave"));
                if (!r.ok()) {
                    Replies.ephemeral(event, ctx, r.error());
                    return;
                }
                repo.update(existing.get().id(), r.value(), name(event));
                edit(event, PixPanelView.manage(accent, repo.list(guildId, userId)));
            }
            case "sendform" -> {
                Optional<PixKey> key = repo.find(Long.parseLong(id.arg(0)));
                if (key.isEmpty()) {
                    Replies.ephemeral(event, ctx, "Chave não encontrada.");
                    return;
                }
                long cents = parseCents(value(event, "valor"));
                PixDispatch.Rendered pix = PixDispatch.render(key.get(), cents, accent, userId, id.arg(1));
                event.replyComponents(pix.container()).useComponentsV2().addFiles(pix.file()).queue();
            }
            default -> { }
        }
    }

    private static Modal keyModal(String action, String arg, String chaveValue, String nomeValue) {
        TextInput.Builder chave = TextInput.create("chave", TextInputStyle.SHORT)
                .setPlaceholder("Valor da chave (será normalizado)").setRequired(true).setMaxLength(80);
        if (chaveValue != null && !chaveValue.isBlank()) {
            chave.setValue(chaveValue);
        }
        TextInput.Builder nome = TextInput.create("nome", TextInputStyle.SHORT)
                .setPlaceholder("Nome do recebedor (máx 25)").setRequired(true).setMaxLength(25);
        if (nomeValue != null && !nomeValue.isBlank()) {
            nome.setValue(nomeValue);
        }
        return Modal.create(ComponentId.of("pix", action, arg), "Chave Pix")
                .addComponents(Label.of("Chave", chave.build()), Label.of("Nome do recebedor", nome.build()))
                .build();
    }

    /** O valor já vem em centavos no id do botão de confirmação (ex.: "2200"). */
    private static long amountCentsArg(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String saleLog(String sellerId, long cents, String clientId) {
        StringBuilder sb = new StringBuilder("# " + Emojis.of(Emojis.SALES, "🛒") + " Venda confirmada\n");
        sb.append(Emojis.of(Emojis.MEMBER, "👤")).append(" **Vendedor** · <@").append(sellerId).append(">\n");
        if (clientId != null && !clientId.isBlank()) {
            sb.append(Emojis.of(Emojis.MEMBER, "🧑")).append(" **Cliente** · <@").append(clientId).append(">\n");
        }
        sb.append(cents > 0
                ? Emojis.of(Emojis.CASH, "💵") + " **Valor** · `" + Money.format(cents) + "`"
                : "-# Valor não informado (cobrança aberta).");
        return sb.toString();
    }

    private static long parseCents(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            double v = Double.parseDouble(raw.trim().replace(',', '.'));
            return v > 0 ? Math.round(v * 100) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String value(ModalInteractionEvent event, String id) {
        ModalMapping mapping = event.getValue(id);
        return mapping == null ? "" : mapping.getAsString();
    }

    private static String name(ModalInteractionEvent event) {
        String n = value(event, "nome").trim();
        return n.isBlank() ? "PIX" : n;
    }

    private void edit(IMessageEditCallback event, Container screen) {
        event.editComponents(screen).useComponentsV2().queue();
    }

    private static PixKeyRepository repo(BotContext ctx) {
        return new PixKeyRepository(ctx.database().sqlite());
    }

    private static int accent(BotContext ctx, String guildId) {
        return EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
    }
}
