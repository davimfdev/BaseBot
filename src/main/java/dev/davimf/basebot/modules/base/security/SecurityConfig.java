package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.database.model.GuildConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the base security config from {@link GuildConfig} (prefix {@code sec:}). Pure and
 * unit-tested, mirroring {@code ModerationConfig}. Covers the AutoMod module + shared
 * exemptions; later security modules (anti-raid, verification, anti-nuke) add their own keys.
 */
public final class SecurityConfig {

    public static final String KEY_AUTOMOD = "sec:automod";
    public static final String KEY_AUTOMOD_WARN = "sec:automod-warn";
    public static final String KEY_WARN_PER = "sec:automod-warn-per";
    public static final String KEY_WINDOW_S = "sec:automod-window-s";
    public static final String KEY_MENTION_LIMIT = "sec:automod-mention-limit";
    public static final String KEY_BLOCK_INVITES = "sec:automod-block-invites";
    public static final String KEY_KEYWORDS = "sec:automod-keywords";
    public static final String KEY_EXEMPT_ROLES = "sec:exempt-roles";
    public static final String KEY_EXEMPT_CHANNELS = "sec:exempt-channels";

    // Verificação (Módulo 3)
    public static final String KEY_VERIFY = "sec:verify";
    public static final String KEY_VERIFY_USERSELECT = "sec:verify-userselect";
    /** Nome lógico do canal (guild_config.channels) onde a fila de aprovação é postada. */
    public static final String CHANNEL_VERIFY = "verificacao";

    // Anti-raid (Módulo 2)
    public static final String KEY_ANTIRAID = "sec:antiraid";
    public static final String KEY_RAID_JOINS = "sec:antiraid-joins";
    public static final String KEY_RAID_WINDOW_S = "sec:antiraid-window-s";
    public static final String KEY_RAID_MIN_AGE = "sec:antiraid-min-age-days";
    public static final String KEY_RAID_LOCK_LEVEL = "sec:antiraid-lock-level";
    public static final String KEY_RAID_PREV_LEVEL = "sec:antiraid-prev-level";

    // Anti-nuke (Módulo 4)
    public static final String KEY_ANTINUKE = "sec:antinuke";
    public static final String KEY_ANTINUKE_MAX = "sec:antinuke-max";
    public static final String KEY_ANTINUKE_WINDOW_S = "sec:antinuke-window-s";
    public static final String KEY_ANTINUKE_WHITELIST = "sec:antinuke-whitelist";

    /** Discord caps custom keyword lists; keep ours well under the limit. */
    public static final int MAX_KEYWORDS = 30;

    private SecurityConfig() {}

    public static boolean automod(GuildConfig cfg) {
        return cfg.toggle(KEY_AUTOMOD, false);
    }

    public static boolean automodWarn(GuildConfig cfg) {
        return cfg.toggle(KEY_AUTOMOD_WARN, true);
    }

    public static boolean blockInvites(GuildConfig cfg) {
        return cfg.toggle(KEY_BLOCK_INVITES, true);
    }

    public static int warnPer(GuildConfig cfg) {
        return intOr(cfg.setting(KEY_WARN_PER), 1, 1);
    }

    public static int windowSeconds(GuildConfig cfg) {
        return intOr(cfg.setting(KEY_WINDOW_S), 0, 0);
    }

    public static int mentionLimit(GuildConfig cfg) {
        return intOr(cfg.setting(KEY_MENTION_LIMIT), 5, 1);
    }

    public static List<String> keywords(GuildConfig cfg) {
        List<String> kw = csv(cfg.setting(KEY_KEYWORDS));
        return kw.size() > MAX_KEYWORDS ? kw.subList(0, MAX_KEYWORDS) : kw;
    }

    public static Set<String> exemptRoleIds(GuildConfig cfg) {
        return new LinkedHashSet<>(csv(cfg.setting(KEY_EXEMPT_ROLES)));
    }

    public static Set<String> exemptChannelIds(GuildConfig cfg) {
        return new LinkedHashSet<>(csv(cfg.setting(KEY_EXEMPT_CHANNELS)));
    }

    /** True when the member (by its role ids) or the channel is exempt from AutoMod. */
    public static boolean isExempt(GuildConfig cfg, Collection<String> memberRoleIds, String channelId) {
        if (channelId != null && exemptChannelIds(cfg).contains(channelId)) {
            return true;
        }
        Set<String> exempt = exemptRoleIds(cfg);
        for (String r : memberRoleIds) {
            if (exempt.contains(r)) {
                return true;
            }
        }
        return false;
    }

    // --- verificação -----------------------------------------------------------

    public static boolean verify(GuildConfig cfg) { return cfg.toggle(KEY_VERIFY, false); }
    public static boolean verifyUserSelect(GuildConfig cfg) { return cfg.toggle(KEY_VERIFY_USERSELECT, false); }

    // --- anti-spam (canal-armadilha) -------------------------------------------

    public static final String KEY_ANTISPAM = "sec:antispam";
    /** Nome lógico do canal-armadilha em guild_config.channels. */
    public static final String CHANNEL_ANTISPAM = "anti-spam";
    /** Quantas mensagens recentes do autor apagar ao punir. */
    public static final int ANTISPAM_PURGE = 10;

    public static boolean antispam(GuildConfig cfg) { return cfg.toggle(KEY_ANTISPAM, false); }

    /** True quando o autor não deve ser punido pelo anti-spam. */
    public static boolean isSpamExempt(GuildConfig cfg, boolean bot, boolean owner, boolean admin,
                                       Collection<String> roleIds) {
        if (bot || owner || admin) {
            return true;
        }
        Set<String> exempt = exemptRoleIds(cfg);
        for (String r : roleIds) {
            if (exempt.contains(r)) {
                return true;
            }
        }
        return false;
    }

    // --- anti-raid -------------------------------------------------------------

    public static boolean antiraid(GuildConfig cfg) { return cfg.toggle(KEY_ANTIRAID, false); }
    public static int raidJoins(GuildConfig cfg) { return intOr(cfg.setting(KEY_RAID_JOINS), 8, 2); }
    public static int raidWindowSeconds(GuildConfig cfg) { return intOr(cfg.setting(KEY_RAID_WINDOW_S), 10, 1); }
    public static int raidMinAgeDays(GuildConfig cfg) { return intOr(cfg.setting(KEY_RAID_MIN_AGE), 7, 0); }

    public static String raidLockLevel(GuildConfig cfg) {
        String v = cfg.setting(KEY_RAID_LOCK_LEVEL);
        return v == null || v.isBlank() ? "HIGH" : v.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public static String raidPrevLevel(GuildConfig cfg) {
        String v = cfg.setting(KEY_RAID_PREV_LEVEL);
        return v == null || v.isBlank() ? null : v.trim();
    }

    // --- anti-nuke -------------------------------------------------------------

    public static boolean antinuke(GuildConfig cfg) { return cfg.toggle(KEY_ANTINUKE, false); }
    public static int antinukeMax(GuildConfig cfg) { return intOr(cfg.setting(KEY_ANTINUKE_MAX), 5, 2); }
    public static int antinukeWindowSeconds(GuildConfig cfg) { return intOr(cfg.setting(KEY_ANTINUKE_WINDOW_S), 60, 5); }

    public static List<String> antinukeWhitelist(GuildConfig cfg) { return csv(cfg.setting(KEY_ANTINUKE_WHITELIST)); }

    /** True quando o ator está na whitelist por id (token {@code user:<id>}) ou por cargo ({@code role:<id>}). */
    public static boolean isNukeWhitelisted(GuildConfig cfg, String userId, Collection<String> roleIds) {
        List<String> wl = antinukeWhitelist(cfg);
        if (wl.contains("user:" + userId)) {
            return true;
        }
        for (String r : roleIds) {
            if (wl.contains("role:" + r)) {
                return true;
            }
        }
        return false;
    }

    private static int intOr(String raw, int def, int min) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            return Math.max(min, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static List<String> csv(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String s : Arrays.stream(raw.split(",")).map(String::trim).toList()) {
            if (!s.isEmpty() && out.stream().noneMatch(i -> i.equalsIgnoreCase(s))) {
                out.add(s);
            }
        }
        return out;
    }
}
