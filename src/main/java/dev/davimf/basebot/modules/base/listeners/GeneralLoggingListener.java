package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * General event logging (BOTSPECS §General Logging). Persists a fast local record of
 * each event to SQLite via {@link dev.davimf.basebot.database.sqlite.ActionLogRepository};
 * channel-facing log embeds (to the configured #logs channel) are layered on top later.
 *
 * <p>Only command-execution logging is wired here as the reference; message edits/
 * deletes, joins/leaves, voice traffic, bans and kicks are scaffolded TODOs.
 */
public final class GeneralLoggingListener extends ListenerAdapter {

    private final BotContext ctx;

    public GeneralLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            return; // guild-scoped logging only (Golden Rule: never global state)
        }
        ctx.database().actionLogs().log(
                event.getGuild().getId(),
                event.getUser().getId(),
                null,
                "COMMAND_EXEC",
                "/" + event.getName()
        );
    }

    // TODO(General Logging): onMessageDelete, onMessageUpdate, onGuildMemberJoin/Remove,
    // onGuildVoiceUpdate, onGuildBan, kick auditing -> action_logs + #logs embeds.
}
