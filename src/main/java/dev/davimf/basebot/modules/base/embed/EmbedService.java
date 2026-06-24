package dev.davimf.basebot.modules.base.embed;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.WebhookSender;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

import java.util.concurrent.CompletableFuture;

/**
 * Builds and dispatches embeds via a managed channel webhook (BOTSPECS Module 1 —
 * /embed, /editembed). Webhook posting allows per-message username/avatar impersonation;
 * editing leaves the message's components (select menus) untouched. The blocking HTTP
 * call runs off the JDA thread on the shared scheduler.
 */
public final class EmbedService {

    private static final String WEBHOOK_NAME = "BaseBot Embed";

    private final BotContext ctx;

    public EmbedService(BotContext ctx) {
        this.ctx = ctx;
    }

    public void send(ModalInteractionEvent event) {
        if (event.getGuild() == null || !(event.getChannel() instanceof TextChannel channel)) {
            event.reply("Use em um canal de texto.").setEphemeral(true).queue();
            return;
        }
        int color = resolveColor(value(event, "cor"), event.getGuild().getId());
        ObjectNode embed = WebhookSender.embed(value(event, "titulo"), value(event, "descricao"), color);
        String nome = value(event, "nome");
        String avatar = value(event, "avatar");

        event.deferReply(true).queue();
        webhook(channel).thenAcceptAsync(wh -> {
            WebhookSender.send(wh.getUrl(), nome, avatar, embed);
            ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                    channel.getId(), "EMBED_SEND", nome);
            event.getHook().sendMessage("✅ Embed enviado.").queue();
        }, ctx.scheduler().executor()).exceptionally(err -> fail(event, err));
    }

    public void edit(ModalInteractionEvent event, String messageId) {
        if (event.getGuild() == null || !(event.getChannel() instanceof TextChannel channel)) {
            event.reply("Use em um canal de texto.").setEphemeral(true).queue();
            return;
        }
        int color = resolveColor(value(event, "cor"), event.getGuild().getId());
        ObjectNode embed = WebhookSender.embed(value(event, "titulo"), value(event, "descricao"), color);

        event.deferReply(true).queue();
        webhook(channel).thenAcceptAsync(wh -> {
            WebhookSender.edit(wh.getUrl(), messageId, embed);
            ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(),
                    messageId, "EMBED_EDIT", null);
            event.getHook().sendMessage("✅ Embed editado.").queue();
        }, ctx.scheduler().executor()).exceptionally(err -> fail(event, err));
    }

    /** Finds the bot's managed webhook in the channel (with a usable token), or creates it. */
    private CompletableFuture<Webhook> webhook(TextChannel channel) {
        return channel.retrieveWebhooks().submit().thenCompose(list -> list.stream()
                .filter(w -> WEBHOOK_NAME.equals(w.getName()) && w.getToken() != null)
                .findFirst()
                .map(CompletableFuture::completedFuture)
                .orElseGet(() -> channel.createWebhook(WEBHOOK_NAME).reason("/embed").submit()));
    }

    private int resolveColor(String hex, String guildId) {
        return EmbedColor.parse(hex).orElseGet(() ->
                EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId)));
    }

    private Void fail(ModalInteractionEvent event, Throwable err) {
        Throwable cause = err.getCause() == null ? err : err.getCause();
        event.getHook().sendMessage("Falha: " + cause.getMessage()
                + "\n-# O bot precisa da permissão **Gerenciar Webhooks** neste canal.").queue();
        return null;
    }

    private static String value(ModalInteractionEvent event, String key) {
        ModalMapping m = event.getValue(key);
        return m == null ? null : m.getAsString();
    }
}
