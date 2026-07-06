package dev.davimf.basebot.modules.base.listeners;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.util.ChannelLog;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.stream.Collectors;

/** Loga a execução de slash commands (com os parâmetros passados) em log-comandos + action_logs. */
public final class CommandLoggingListener extends ListenerAdapter {

    private final BotContext ctx;

    public CommandLoggingListener(BotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            return;
        }
        String params = event.getOptions().stream()
                .map(o -> "`" + o.getName() + "`: " + o.getAsString())
                .collect(Collectors.joining(", "));
        ctx.database().actionLogs().log(event.getGuild().getId(), event.getUser().getId(), null,
                "COMMAND_EXEC", event.getCommandString());

        String body = "## " + Emojis.of(Emojis.MESSAGE, "💬") + " Comando\n---\n"
                + "**Comando** · `/" + event.getFullCommandName() + "`"
                + (params.isEmpty() ? "" : "\n**Parâmetros** · " + params)
                + "\n---\n"
                + Emojis.of(Emojis.MEMBER, "👤") + " **Por** · " + event.getUser().getAsMention()
                + "\n" + Emojis.of(Emojis.LOCATION, "📍") + " **Canal** · "
                + (event.getChannel() == null ? "—" : event.getChannel().getAsMention());
        ChannelLog.post(ctx, event.getGuild().getId(), "log-comandos", body);
    }
}
