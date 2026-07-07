package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

/** Anti-spam do canal-armadilha: expulsa o autor e apaga suas N mensagens mais recentes
 *  varrendo os canais de texto visíveis. A seleção das mais recentes é pura e testável. */
public final class AntiSpamService {

    private AntiSpamService() {}

    /** Ordena {@code items} do mais recente (maior timestamp) ao mais antigo e retorna até {@code limit}. */
    public static <T> List<T> selectMostRecent(List<T> items, ToLongFunction<T> epochMillis, int limit) {
        List<T> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingLong(epochMillis).reversed());
        return sorted.size() > limit ? new ArrayList<>(sorted.subList(0, limit)) : sorted;
    }

    /** Expulsa {@code author} e apaga suas mensagens recentes. Best-effort e assíncrono. */
    public static void handle(BotContext ctx, Guild guild, Member author) {
        String reason = "Anti-spam: mensagem no canal proibido";
        if (guild.getSelfMember().hasPermission(Permission.KICK_MEMBERS)
                && guild.getSelfMember().canInteract(author)) {
            purgeThenKick(ctx, guild, author, reason);
        } else {
            purge(ctx, guild, author.getId());
        }
    }

    private static void purgeThenKick(BotContext ctx, Guild guild, Member author, String reason) {
        // Apaga primeiro (o membro ainda existe), depois expulsa.
        purge(ctx, guild, author.getId());
        guild.kick(author).reason(reason).queue(ok -> {}, err -> {});
    }

    private static void purge(BotContext ctx, Guild guild, String authorId) {
        List<Message> collected = new ArrayList<>();
        List<TextChannel> channels = guild.getTextChannels().stream()
                .filter(ch -> guild.getSelfMember().hasPermission(ch,
                        Permission.MESSAGE_HISTORY, Permission.MESSAGE_MANAGE))
                .toList();
        collectRecursive(ctx, guild, authorId, channels, 0, collected);
    }

    private static void collectRecursive(BotContext ctx, Guild guild, String authorId,
                                         List<TextChannel> channels, int idx, List<Message> acc) {
        if (idx >= channels.size()) {
            deleteSelected(guild, acc);
            return;
        }
        TextChannel ch = channels.get(idx);
        ch.getHistory().retrievePast(50).queue(msgs -> {
            for (Message m : msgs) {
                if (m.getAuthor().getId().equals(authorId)) {
                    acc.add(m);
                }
            }
            collectRecursive(ctx, guild, authorId, channels, idx + 1, acc);
        }, err -> collectRecursive(ctx, guild, authorId, channels, idx + 1, acc));
    }

    private static void deleteSelected(Guild guild, List<Message> all) {
        List<Message> recent = selectMostRecent(all, m -> m.getTimeCreated().toInstant().toEpochMilli(),
                SecurityConfig.ANTISPAM_PURGE);
        for (Message m : recent) {
            m.delete().reason("Anti-spam: limpeza").queue(ok -> {}, err -> {});
        }
    }
}
