package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * Reference command: replies with the gateway heartbeat. Exists to prove the
 * command framework (registration + routing + BotContext access) works end-to-end.
 */
public final class PingCommand implements SlashCommand {

    @Override
    public String name() {
        return "ping";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("ping", "Verifica a latência do bot.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        long gateway = event.getJDA().getGatewayPing();
        event.reply("Pong! Gateway: " + gateway + "ms").setEphemeral(true).queue();
    }
}
