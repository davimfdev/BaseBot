package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.component.ComponentId;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
import dev.davimf.basebot.modules.base.setup.GuildConfigEdits;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.List;

/** Activates/reverts the anti-raid lockdown (raises the guild verification level, keeping the
 *  previous one in guild_config to restore). Posts an alert with a "Desativar lockdown" button. */
public final class AntiRaidService {

    /** Namespace for security buttons posted outside /setup (e.g. the lockdown alert). */
    public static final String NS = "sec";

    private AntiRaidService() {}

    public static synchronized void lockdown(BotContext ctx, Guild guild) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        if (SecurityConfig.raidPrevLevel(cfg) != null) {
            return; // já em lockdown
        }
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_SERVER)) {
            ChannelLog.post(ctx, guild.getId(), ModerationService.MODLOG_KEY,
                    "## " + Emojis.of(Emojis.WARN, "⚠️") + " Possível raid detectado\n---\n"
                            + "-# Não consegui subir a verificação (falta **Gerenciar Servidor**).");
            return;
        }
        Guild.VerificationLevel prev = guild.getVerificationLevel();
        Guild.VerificationLevel target = parseLevel(SecurityConfig.raidLockLevel(cfg));
        ctx.database().guildConfig().save(GuildConfigEdits.withSetting(cfg,
                SecurityConfig.KEY_RAID_PREV_LEVEL, prev.name()));
        guild.getManager().setVerificationLevel(target).queue(ok -> {}, err -> {});

        TextChannel modlog = modlog(ctx, guild);
        if (modlog != null) {
            String body = "## " + Emojis.of(Emojis.SHIELD, "🛡️") + " Lockdown ativado (anti-raid)\n---\n"
                    + "**Verificação** · `" + prev.name() + "` → `" + target.name() + "`\n"
                    + "-# Surto de entradas detectado. Desative quando passar.";
            modlog.sendMessageComponents(Panels.container(EmbedColor.resolve(cfg), Panels.text(body),
                            ActionRow.of(Button.danger(ComponentId.of(NS, "raidunlock"), "Desativar lockdown")
                                    .withEmoji(Emojis.button(Emojis.UNLOCK)))))
                    .useComponentsV2().setAllowedMentions(List.of()).queue(ok -> {}, err -> {});
        }
    }

    public static synchronized void unlock(BotContext ctx, Guild guild) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guild.getId());
        String prev = SecurityConfig.raidPrevLevel(cfg);
        if (prev == null) {
            return;
        }
        guild.getManager().setVerificationLevel(parseLevel(prev)).queue(ok -> {}, err -> {});
        ctx.database().guildConfig().save(GuildConfigEdits.withSetting(cfg, SecurityConfig.KEY_RAID_PREV_LEVEL, ""));
        ChannelLog.post(ctx, guild.getId(), ModerationService.MODLOG_KEY,
                "## " + Emojis.of(Emojis.CHECK_YES, "✅") + " Lockdown desativado\n---\n"
                        + "**Verificação restaurada** · `" + prev + "`");
    }

    private static Guild.VerificationLevel parseLevel(String name) {
        try {
            return Guild.VerificationLevel.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Guild.VerificationLevel.HIGH;
        }
    }

    private static TextChannel modlog(BotContext ctx, Guild guild) {
        String id = ctx.database().guildConfig().findOrEmpty(guild.getId()).channel(ModerationService.MODLOG_KEY);
        return id == null ? null : guild.getTextChannelById(id);
    }
}
