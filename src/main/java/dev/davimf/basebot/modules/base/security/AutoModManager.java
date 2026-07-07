package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.database.model.GuildConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.automod.AutoModResponse;
import net.dv8tion.jda.api.entities.automod.AutoModRule;
import net.dv8tion.jda.api.entities.automod.build.AutoModRuleData;
import net.dv8tion.jda.api.entities.automod.build.TriggerConfig;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Syncs the guild's native AutoMod rules with {@link SecurityConfig}. Rules managed by the bot
 * carry the {@link #PREFIX} so manual rules from the owner are never touched. Idempotent:
 * creates the desired rules that are missing and deletes the bot's rules that are no longer
 * wanted. Config changes are applied by delete+recreate (v1). Requires MANAGE_SERVER.
 */
public final class AutoModManager {

    public static final String PREFIX = "BaseBot · ";

    private record Desired(String name, AutoModRuleData data) {}

    private AutoModManager() {}

    /** BLOCKING — call off the gateway thread (e.g. ctx.scheduler().executor()). */
    public static void sync(Guild guild, GuildConfig cfg) {
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_SERVER)) {
            return;
        }
        List<Role> exemptRoles = SecurityConfig.exemptRoleIds(cfg).stream()
                .map(guild::getRoleById).filter(Objects::nonNull).collect(Collectors.toList());
        List<GuildChannel> exemptCh = SecurityConfig.exemptChannelIds(cfg).stream()
                .map(guild::getGuildChannelById).filter(Objects::nonNull).collect(Collectors.toList());

        List<Desired> desired = new ArrayList<>();
        if (SecurityConfig.automod(cfg)) {
            desired.add(rule(PREFIX + "Spam", TriggerConfig.antiSpam(), exemptRoles, exemptCh));
            desired.add(rule(PREFIX + "Mention Spam",
                    TriggerConfig.mentionSpam(SecurityConfig.mentionLimit(cfg)), exemptRoles, exemptCh));
            desired.add(rule(PREFIX + "Palavroes", TriggerConfig.presetKeywordFilter(
                    AutoModRule.KeywordPreset.PROFANITY,
                    AutoModRule.KeywordPreset.SEXUAL_CONTENT,
                    AutoModRule.KeywordPreset.SLURS), exemptRoles, exemptCh));

            List<String> kw = new ArrayList<>();
            if (SecurityConfig.blockInvites(cfg)) {
                kw.add("discord.gg/*");
                kw.add("discord.com/invite/*");
                kw.add("discordapp.com/invite/*");
            }
            kw.addAll(SecurityConfig.keywords(cfg));
            if (!kw.isEmpty()) {
                desired.add(rule(PREFIX + "Links/Keywords", TriggerConfig.keywordFilter(kw), exemptRoles, exemptCh));
            }
        }

        List<AutoModRule> existing;
        try {
            existing = guild.retrieveAutoModRules().complete().stream()
                    .filter(r -> r.getName().startsWith(PREFIX)).collect(Collectors.toList());
        } catch (RuntimeException e) {
            return; // sem permissão / falha de API — degrada em silêncio
        }
        // v1: refresh by delete+recreate — drop all of ours, then create the desired set.
        for (AutoModRule r : existing) {
            r.delete().queue(ok -> {}, err -> {});
        }
        for (Desired d : desired) {
            guild.createAutoModRule(d.data()).queue(ok -> {}, err -> {});
        }
    }

    private static Desired rule(String name, TriggerConfig trigger, List<Role> roles, List<GuildChannel> chans) {
        AutoModRuleData data = AutoModRuleData.onMessage(name, trigger)
                .setEnabled(true)
                .putResponses(AutoModResponse.blockMessage());
        if (!roles.isEmpty()) {
            data.setExemptRoles(roles);
        }
        if (!chans.isEmpty()) {
            data.setExemptChannels(chans);
        }
        return new Desired(name, data);
    }
}
