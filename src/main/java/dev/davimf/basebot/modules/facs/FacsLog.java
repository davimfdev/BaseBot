package dev.davimf.basebot.modules.facs;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.ChannelLog;

/**
 * Posts a Components V2 log entry to one of the Facs log channels (BOTSPECS §Module 4).
 * Thin alias over {@link ChannelLog} kept for call-site readability in the Facs module.
 */
public final class FacsLog {

    private FacsLog() {}

    public static void post(BotContext ctx, String guildId, String logKey, String markdown) {
        ChannelLog.post(ctx, guildId, logKey, markdown);
    }
}
