package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.fun.ForcaService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /forca — inicia um jogo da forca no canal. */
public final class ForcaCommand implements SlashCommand {
    private final ForcaService service;

    public ForcaCommand(ForcaService service) { this.service = service; }

    @Override public String name() { return "forca"; }

    @Override public SlashCommandData data() {
        return Commands.slash("forca", "Inicia um jogo da forca no canal.");
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        service.start(event);
    }
}
