package dev.davimf.basebot.modules.base.moderation;

import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.util.Durations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads/writes the base moderation config from {@link GuildConfig} (the Postgres source of
 * truth, dashboard-editable). Escalation rules + warn TTL live in {@code settings}; the two
 * behaviour flags in {@code toggles}. Parsing/serialization is pure and unit-tested.
 *
 * <p>Escalation string grammar: comma-separated {@code <threshold>=<action>[:<duration>]},
 * e.g. {@code 3=timeout:1h,5=kick,7=ban}. Actions needing a duration: timeout, tempban,
 * mute. Actions without: kick, ban. Malformed entries are dropped.
 */
public final class ModerationConfig {

    public static final String KEY_WARN_TTL = "mod:warn-ttl-days";
    public static final String KEY_ESCALATION = "mod:escalation";
    public static final String KEY_DM = "mod:dm-on-action";
    public static final String KEY_REQUIRE_REASON = "mod:require-reason";

    public static final String ACTION_TIMEOUT = "timeout";
    public static final String ACTION_TEMPBAN = "tempban";
    public static final String ACTION_MUTE = "mute";
    public static final String ACTION_KICK = "kick";
    public static final String ACTION_BAN = "ban";

    private static final Set<String> NEEDS_DURATION = Set.of(ACTION_TIMEOUT, ACTION_TEMPBAN, ACTION_MUTE);
    private static final Set<String> VALID_ACTIONS =
            Set.of(ACTION_TIMEOUT, ACTION_TEMPBAN, ACTION_MUTE, ACTION_KICK, ACTION_BAN);

    private ModerationConfig() {}

    /** One auto-escalation step: at {@code threshold} active warns, apply {@code action}. */
    public record EscalationRule(int threshold, String action, Long durationMs) {

        public boolean needsDuration() {
            return NEEDS_DURATION.contains(action);
        }

        /** Human description for the setup overview, e.g. "3 → timeout 1h". */
        public String describe() {
            return threshold + " → " + action + (durationMs != null ? " " + Durations.format(durationMs) : "");
        }
    }

    // --- reads -----------------------------------------------------------------

    public static int warnTtlDays(GuildConfig cfg) {
        String raw = cfg.setting(KEY_WARN_TTL);
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Warn lifetime in millis, or 0 when warns never expire. */
    public static long warnTtlMillis(GuildConfig cfg) {
        return warnTtlDays(cfg) * 86_400_000L;
    }

    public static List<EscalationRule> escalation(GuildConfig cfg) {
        return parse(cfg.setting(KEY_ESCALATION));
    }

    /** The escalation rule whose threshold exactly equals the current warn count, or null. */
    public static EscalationRule escalationFor(GuildConfig cfg, int warnCount) {
        for (EscalationRule r : escalation(cfg)) {
            if (r.threshold() == warnCount) {
                return r;
            }
        }
        return null;
    }

    public static boolean dmOnAction(GuildConfig cfg) {
        return cfg.toggle(KEY_DM, true);
    }

    public static boolean requireReason(GuildConfig cfg) {
        return cfg.toggle(KEY_REQUIRE_REASON, false);
    }

    // --- parse / serialize (pure) ----------------------------------------------

    public static List<EscalationRule> parse(String raw) {
        List<EscalationRule> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split(",")) {
            EscalationRule rule = parseRule(part.trim());
            if (rule != null) {
                out.add(rule);
            }
        }
        return out;
    }

    private static EscalationRule parseRule(String token) {
        int eq = token.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        int threshold;
        try {
            threshold = Integer.parseInt(token.substring(0, eq).trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (threshold <= 0) {
            return null;
        }
        String spec = token.substring(eq + 1).trim().toLowerCase();
        int colon = spec.indexOf(':');
        String action = (colon < 0 ? spec : spec.substring(0, colon)).trim();
        if (!VALID_ACTIONS.contains(action)) {
            return null;
        }
        if (NEEDS_DURATION.contains(action)) {
            String durStr = colon < 0 ? "" : spec.substring(colon + 1).trim();
            var parsed = Durations.parse(durStr);
            if (parsed.isEmpty()) {
                return null;
            }
            return new EscalationRule(threshold, action, parsed.getAsLong());
        }
        return new EscalationRule(threshold, action, null);
    }

    public static String serialize(List<EscalationRule> rules) {
        StringBuilder sb = new StringBuilder();
        for (EscalationRule r : rules) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(r.threshold()).append('=').append(r.action());
            if (r.durationMs() != null) {
                sb.append(':').append(Durations.format(r.durationMs()).replace(" ", ""));
            }
        }
        return sb.toString();
    }
}
