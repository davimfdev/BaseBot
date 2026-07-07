package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.setup.SetupLogTypes;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Esconde/reabre canais quando a verificação liga/desliga. Ao ligar, canais não-log abertos
 *  ficam invisíveis para @everyone e visíveis para o cargo membro. Ao desligar, reverte apenas
 *  os canais com a assinatura de lockdown (@everyone nega VIEW + membro permite VIEW). A decisão
 *  de exclusão/estado/assinatura é pura e testável; as edições de permissão são efeito JDA. */
public final class VerificationLockdown {

    /** Resultado de um sweep. {@code memberRoleMissing} = cargo membro não configurado (nada feito). */
    public record Summary(int changed, int skipped, boolean memberRoleMissing, boolean busy) {}

    private VerificationLockdown() {}

    /** Uma varredura por vez por servidor: evita apply/revert concorrentes intercalando edições
     *  de permissão nos mesmos canais (o executor tem poucas threads). */
    private static final Set<String> INFLIGHT = ConcurrentHashMap.newKeySet();

    // ---- Helpers puros (testáveis) ----

    /** Ids que nunca são escondidos nem revertidos: canais de log + verificação + anti-spam. */
    public static Set<String> excludedChannelIds(GuildConfig cfg) {
        Set<String> ids = new LinkedHashSet<>();
        for (SetupLogTypes.LogType t : SetupLogTypes.ALL) {
            String id = cfg.channel(t.key());
            if (id != null) {
                ids.add(id);
            }
        }
        addIfPresent(ids, cfg.channel(SecurityConfig.CHANNEL_VERIFY));
        addIfPresent(ids, cfg.channel(SecurityConfig.CHANNEL_ANTISPAM));
        return ids;
    }

    private static void addIfPresent(Set<String> ids, String id) {
        if (id != null) {
            ids.add(id);
        }
    }

    /** True quando @everyone NÃO tem VIEW_CHANNEL negado (canal "aberto"). null = vazio. */
    public static boolean isOpenForEveryone(Collection<Permission> everyoneDenied) {
        return everyoneDenied == null || !everyoneDenied.contains(Permission.VIEW_CHANNEL);
    }

    /** True só quando @everyone nega VIEW_CHANNEL E o cargo membro permite VIEW_CHANNEL. null = vazio. */
    public static boolean matchesLockdownSignature(Collection<Permission> everyoneDenied,
                                                   Collection<Permission> memberAllowed) {
        return everyoneDenied != null && everyoneDenied.contains(Permission.VIEW_CHANNEL)
                && memberAllowed != null && memberAllowed.contains(Permission.VIEW_CHANNEL);
    }

    // ---- Efeitos JDA (não unit-testados; rodar no executor, nunca na thread do gateway) ----

    /** Esconde os canais não-log abertos: nega VIEW p/ @everyone, concede p/ membro. */
    public static Summary apply(BotContext ctx, Guild guild) {
        if (!INFLIGHT.add(guild.getId())) {
            return new Summary(0, 0, false, true); // já há uma varredura rodando neste servidor
        }
        try {
            GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
            Role membro = memberRole(cfg, guild);
            if (membro == null) {
                return new Summary(0, 0, true, false);
            }
            Role everyone = guild.getPublicRole();
            Set<String> excluded = excludedChannelIds(cfg);
            int changed = 0;
            int skipped = 0;
            for (GuildChannel ch : guild.getChannels()) {
                if (!(ch instanceof IPermissionContainer pc) || excluded.contains(ch.getId())) {
                    continue;
                }
                PermissionOverride everyOv = pc.getPermissionOverride(everyone);
                if (!isOpenForEveryone(everyOv == null ? null : everyOv.getDenied())) {
                    continue; // já fechado
                }
                if (!guild.getSelfMember().hasPermission(ch, Permission.MANAGE_PERMISSIONS)) {
                    skipped++;
                    continue;
                }
                try {
                    // Ordem segura: concede ao membro ANTES de negar @everyone. Se o grant falhar, o
                    // deny nem acontece e o canal continua aberto (nunca fica escondido sem escape).
                    pc.upsertPermissionOverride(membro).grant(Permission.VIEW_CHANNEL).complete();
                    pc.upsertPermissionOverride(everyone).deny(Permission.VIEW_CHANNEL).complete();
                    changed++;
                } catch (RuntimeException e) {
                    skipped++;
                }
            }
            return new Summary(changed, skipped, false, false);
        } finally {
            INFLIGHT.remove(guild.getId());
        }
    }

    /** Reabre apenas os canais com a assinatura de lockdown; excluídos nunca são revertidos. */
    public static Summary revert(BotContext ctx, Guild guild) {
        if (!INFLIGHT.add(guild.getId())) {
            return new Summary(0, 0, false, true); // já há uma varredura rodando neste servidor
        }
        try {
            GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
            Role membro = memberRole(cfg, guild);
            if (membro == null) {
                return new Summary(0, 0, true, false);
            }
            Role everyone = guild.getPublicRole();
            Set<String> excluded = excludedChannelIds(cfg);
            int changed = 0;
            int skipped = 0;
            for (GuildChannel ch : guild.getChannels()) {
                if (!(ch instanceof IPermissionContainer pc) || excluded.contains(ch.getId())) {
                    continue;
                }
                PermissionOverride everyOv = pc.getPermissionOverride(everyone);
                PermissionOverride membroOv = pc.getPermissionOverride(membro);
                boolean matches = matchesLockdownSignature(
                        everyOv == null ? null : everyOv.getDenied(),
                        membroOv == null ? null : membroOv.getAllowed());
                if (!matches) {
                    continue;
                }
                if (!guild.getSelfMember().hasPermission(ch, Permission.MANAGE_PERMISSIONS)) {
                    skipped++;
                    continue;
                }
                try {
                    // Ordem segura: reabre @everyone primeiro; o allow do membro é só redundante.
                    pc.upsertPermissionOverride(everyone).clear(Permission.VIEW_CHANNEL).complete();
                    // Best-effort: se limpar o allow do membro falhar, o canal já está reaberto — o
                    // override redundante não afeta a visibilidade; conta como changed do mesmo jeito.
                    try {
                        pc.upsertPermissionOverride(membro).clear(Permission.VIEW_CHANNEL).complete();
                    } catch (RuntimeException ignored) {
                        // deixa o allow redundante; canal já reaberto
                    }
                    changed++;
                } catch (RuntimeException e) {
                    skipped++;
                }
            }
            return new Summary(changed, skipped, false, false);
        } finally {
            INFLIGHT.remove(guild.getId());
        }
    }

    private static Role memberRole(GuildConfig cfg, Guild guild) {
        String id = cfg.role("membro");
        return id == null ? null : guild.getRoleById(id);
    }
}
