package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.fun.QuizPlayService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /quiz — inicia um quiz (perguntas do servidor, ou banco embutido). */
public final class QuizCommand implements SlashCommand {
    private final QuizPlayService service;

    public QuizCommand(QuizPlayService service) { this.service = service; }

    @Override public String name() { return "quiz"; }

    @Override public SlashCommandData data() {
        return Commands.slash("quiz", "Inicia um quiz no canal.");
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        service.start(event);
    }
}
