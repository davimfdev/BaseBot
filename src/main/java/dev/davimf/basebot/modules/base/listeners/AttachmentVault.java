package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Re-hosts message attachments to a central <b>vault guild</b> so deleted/edited attachments stay
 * viewable forever (Discord deletes/expires CDN URLs). Inside the vault guild there is one text
 * channel per origin guild, named with the origin guild id; each archived message is re-uploaded
 * there with the original message id as the content. The mapping (vault channel + message id) is
 * stored in {@code message_archive}; on demand we re-fetch the vault message for fresh URLs.
 *
 * <p>Disabled when no {@code VAULT_GUILD_ID} is configured.
 */
public final class AttachmentVault {

    private final BotContext ctx;
    private final String vaultGuildId;
    /** originGuildId -> vault channel id (cache to avoid repeated lookups). */
    private final Map<String, String> channels = new ConcurrentHashMap<>();

    public AttachmentVault(BotContext ctx, String vaultGuildId) {
        this.ctx = ctx;
        this.vaultGuildId = vaultGuildId;
    }

    public boolean enabled() {
        return vaultGuildId != null && !vaultGuildId.isBlank() && ctx.jda() != null;
    }

    /**
     * Re-uploads {@code m}'s attachments to the per-origin-guild vault channel (content = original
     * message id). On success calls {@code onArchived(vaultChannelId, vaultMessageId)}. Oversized
     * attachments (above the vault guild's upload limit) are skipped. Never throws.
     */
    public void archive(Message m, BiConsumer<String, String> onArchived) {
        if (!enabled() || !m.isFromGuild() || m.getAttachments().isEmpty()) {
            return;
        }
        Guild vault = ctx.jda().getGuildById(vaultGuildId);
        if (vault == null) {
            return;
        }
        long max = vault.getMaxFileSize();
        List<FileUpload> files = new ArrayList<>();
        for (Message.Attachment a : m.getAttachments()) {
            if (a.getSize() <= max) {
                files.add(a.getProxy().downloadAsFileUpload(a.getFileName()));
            }
        }
        if (files.isEmpty()) {
            return; // all oversized
        }
        String originId = m.getId();
        resolveChannel(vault, m.getGuild().getId(), channel -> {
            if (channel == null) {
                closeQuietly(files);
                return;
            }
            channel.sendMessage(originId).setFiles(files).queue(
                    sent -> onArchived.accept(channel.getId(), sent.getId()),
                    err -> closeQuietly(files));
        });
    }

    /**
     * Re-hospeda uma imagem avulsa (não vinda de uma mensagem) no canal do vault da guilda de
     * origem, usando {@code label} como conteúdo (ex.: "welcome-banner <guildId>"). Em sucesso
     * chama {@code onStored(vaultChannelId, vaultMessageId)}. Em qualquer falha (vault desabilitado,
     * guilda/canal ausente, erro de envio) chama {@code onFail}. Best-effort; nunca lança.
     */
    public void store(String originGuildId, String label, FileUpload file,
                      java.util.function.BiConsumer<String, String> onStored, Runnable onFail) {
        if (!enabled()) {
            closeQuietly(java.util.List.of(file));
            onFail.run();
            return;
        }
        Guild vault = ctx.jda().getGuildById(vaultGuildId);
        if (vault == null) {
            closeQuietly(java.util.List.of(file));
            onFail.run();
            return;
        }
        resolveChannel(vault, originGuildId, channel -> {
            if (channel == null) {
                closeQuietly(java.util.List.of(file));
                onFail.run();
                return;
            }
            channel.sendMessage(label).setFiles(file).queue(
                    sent -> onStored.accept(channel.getId(), sent.getId()),
                    err -> { closeQuietly(java.util.List.of(file)); onFail.run(); });
        });
    }

    private static void closeQuietly(List<FileUpload> files) {
        for (FileUpload f : files) {
            try {
                f.close();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    /** Re-fetches a vault message to get fresh (signed) attachment URLs. Never throws. */
    public void retrieveUrls(String vaultChannelId, String vaultMessageId, Consumer<List<String>> cb) {
        if (vaultChannelId == null || vaultMessageId == null || ctx.jda() == null) {
            cb.accept(List.of());
            return;
        }
        TextChannel c = ctx.jda().getTextChannelById(vaultChannelId);
        if (c == null) {
            cb.accept(List.of());
            return;
        }
        c.retrieveMessageById(vaultMessageId).queue(msg -> {
            List<String> urls = new ArrayList<>();
            msg.getAttachments().forEach(a -> urls.add(a.getUrl()));
            cb.accept(urls);
        }, err -> cb.accept(List.of()));
    }

    private void resolveChannel(Guild vault, String originGuildId, Consumer<TextChannel> cb) {
        String cached = channels.get(originGuildId);
        if (cached != null) {
            TextChannel c = vault.getTextChannelById(cached);
            if (c != null) {
                cb.accept(c);
                return;
            }
        }
        TextChannel existing = vault.getTextChannelsByName(originGuildId, true).stream().findFirst().orElse(null);
        if (existing != null) {
            channels.put(originGuildId, existing.getId());
            cb.accept(existing);
            return;
        }
        vault.createTextChannel(originGuildId).queue(
                c -> {
                    channels.put(originGuildId, c.getId());
                    cb.accept(c);
                },
                err -> cb.accept(null));
    }
}
