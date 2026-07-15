package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.sqlite.MessageArchiveRepository;
import dev.davimf.basebot.modules.base.moderation.PurgeLogSuppressor;
import dev.davimf.basebot.util.AuditLookup;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.stream.Collectors;

/** Logs de mensagem (apagada/editada/bulk/fixada) em log-mensagens, recuperando conteúdo do
 *  arquivo SQLite (message_archive) e os anexos re-hospedados do cofre (AttachmentVault). */
public final class MessageLoggingListener extends ListenerAdapter {

    private final BotContext ctx;
    private final AttachmentVault vault;
    private final PurgeLogSuppressor purgeSuppressor;

    public MessageLoggingListener(BotContext ctx, AttachmentVault vault, PurgeLogSuppressor purgeSuppressor) {
        this.ctx = ctx;
        this.vault = vault;
        this.purgeSuppressor = purgeSuppressor;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            return;
        }
        Message m = event.getMessage();
        if (m.getType() == MessageType.CHANNEL_PINNED_ADD) {
            AuditLookup.lookup(event.getGuild(), null, ActionType.MESSAGE_PIN, actor ->
                    ChannelLog.post(ctx, event.getGuild().getId(), "log-mensagens",
                            "## " + Emojis.of(Emojis.PIN, "📌") + " Mensagem fixada\n---\n"
                                    + Emojis.of(Emojis.LOCATION, "📍") + " **Canal** · " + event.getChannel().getAsMention()
                                    + "\n---\n" + Emojis.of(Emojis.SHIELD, "🛡️") + " **Por** · " + actor.moderatorMention()));
            return;
        }
        if (event.getAuthor().isBot()) {
            return;
        }
        String attachments = m.getAttachments().stream().map(a -> a.getUrl()).collect(Collectors.joining("\n"));
        ctx.database().messageArchive().upsert(m.getId(), event.getGuild().getId(),
                event.getChannel().getId(), event.getAuthor().getId(),
                m.getContentDisplay(), attachments, System.currentTimeMillis());
        // Re-host attachments to the vault so they survive deletion; record the vault location.
        vault.archive(m, (vaultChannelId, vaultMessageId) ->
                ctx.database().messageArchive().setVault(m.getId(), vaultChannelId, vaultMessageId));
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) {
            return;
        }
        MessageArchiveRepository archive = ctx.database().messageArchive();
        MessageArchiveRepository.Archived old = archive.find(event.getMessageId());
        boolean beforeKnown = !(old == null || old.content() == null || old.content().isBlank());
        String now = event.getMessage().getContentDisplay();
        archive.updateContent(event.getMessageId(), now, System.currentTimeMillis());
        if (beforeKnown && old.content().equals(now)) {
            return; // edição sem mudança de texto (ex.: embed) — ignora
        }
        String antes = beforeKnown ? codeBlock(old.content()) : " · *desconhecido*";
        String depois = now.isBlank() ? " · *vazio*" : codeBlock(now);
        String body = "## " + Emojis.of(Emojis.EDIT, "✏️") + " Mensagem editada\n---\n"
                + Emojis.of(Emojis.MEMBER, "👤") + " **Autor** · " + event.getAuthor().getAsMention()
                + "\n" + Emojis.of(Emojis.LOCATION, "📍") + " **Canal** · " + event.getChannel().getAsMention()
                + "\n---\n**Antes**" + antes
                + "\n---\n**Depois**" + depois;
        String gid = event.getGuild().getId();
        if (old != null && old.vaultMessageId() != null) {
            vault.retrieveUrls(old.vaultChannelId(), old.vaultMessageId(),
                    urls -> ChannelLog.post(ctx, gid, "log-mensagens", body, null, urls));
        } else {
            ChannelLog.post(ctx, gid, "log-mensagens", body);
        }
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (!event.isFromGuild()) {
            return;
        }
        // Deleção feita por /purge, /clear ou /cl — não loga (evita floodar o log).
        if (purgeSuppressor.claim(event.getMessageIdLong())) {
            return;
        }
        MessageArchiveRepository.Archived old = ctx.database().messageArchive().find(event.getMessageId());
        // Só arquivamos mensagens de humanos; sem registro = mensagem de bot (inclusive as do
        // próprio bot) ou não capturada. Nesses casos não há o que logar de útil, então ignoramos.
        if (old == null) {
            return;
        }
        String autor = "<@" + old.authorId() + ">";
        boolean hasContent = !(old.content() == null || old.content().isBlank());
        String conteudo = hasContent ? codeBlock(old.content()) : " · *sem texto*";
        String baseBody = "## " + Emojis.of(Emojis.TRASH, "🗑️") + " Mensagem apagada\n---\n"
                + Emojis.of(Emojis.MEMBER, "👤") + " **Autor** · " + autor
                + "\n" + Emojis.of(Emojis.LOCATION, "📍") + " **Canal** · " + event.getChannel().getAsMention()
                + "\n---\n" + Emojis.of(Emojis.MESSAGE, "💬") + " **Conteúdo**" + conteudo;
        String gid = event.getGuild().getId();

        if (old.vaultMessageId() != null) {
            // Preserved attachments from the vault (fresh URLs), shown as images.
            vault.retrieveUrls(old.vaultChannelId(), old.vaultMessageId(),
                    urls -> ChannelLog.post(ctx, gid, "log-mensagens", baseBody, null, urls));
        } else {
            String anexos = old.attachments() == null || old.attachments().isBlank()
                    ? "" : "\n" + Emojis.of(Emojis.ATTACHMENT, "📎") + " **Anexos** · " + old.attachments();
            ChannelLog.post(ctx, gid, "log-mensagens", baseBody + anexos);
        }
    }

    @Override
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        // Deleção em massa disparada por /purge, /clear ou /cl — não loga (evita floodar o log).
        if (purgeSuppressor.claimAny(event.getMessageIds().stream().map(Long::parseLong).toList())) {
            return;
        }
        ChannelLog.post(ctx, event.getGuild().getId(), "log-mensagens",
                "## " + Emojis.of(Emojis.BROOM, "🧹") + " Deleção em massa\n---\n"
                        + Emojis.of(Emojis.LOCATION, "📍") + " **Canal** · " + event.getChannel().getAsMention()
                        + "\n" + Emojis.of(Emojis.HASH, "🔢") + " **Quantidade** · `" + event.getMessageIds().size() + "` mensagens");
    }

    /** Embrulha o conteúdo num bloco de código plaintext (markdown/menções não renderizam).
     *  Crases triplas e linhas "---" no conteúdo são neutralizadas p/ não quebrar o layout. */
    private static String codeBlock(String content) {
        String safe = trim(content).replace("```", "`​`​`").replaceAll("(?m)^(-{3,})$", "​$1");
        return "\n```plaintext\n" + safe + "\n```";
    }

    private static String trim(String s) {
        return s.length() > 800 ? s.substring(0, 800) + "…" : s;
    }
}
