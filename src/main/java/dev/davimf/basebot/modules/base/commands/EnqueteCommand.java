package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.utility.EnqueteService;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/** /enquete — votação com botões. */
public final class EnqueteCommand implements SlashCommand {
    private final EnqueteService service;
    public EnqueteCommand(EnqueteService service) { this.service = service; }

    @Override public String name() { return "enquete"; }

    @Override public SlashCommandData data() {
        return Commands.slash("enquete", "Cria uma votação com botões.")
                .addOptions(new OptionData(OptionType.STRING, "pergunta", "A pergunta", true))
                .addOptions(new OptionData(OptionType.STRING, "opcoes", "Opções separadas por | (2 a 6)", true));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        String pergunta = event.getOption("pergunta", OptionMapping::getAsString);
        OptionMapping opcoesOpt = event.getOption("opcoes");
        List<String> options = EnqueteService.parseOptions(opcoesOpt == null ? "" : opcoesOpt.getAsString());
        if (options.size() < 2 || options.size() > 6) {
            Replies.ephemeral(event, ctx, "Informe de 2 a 6 opções separadas por `|`.");
            return;
        }
        service.create(event, pergunta, options);
    }
}
