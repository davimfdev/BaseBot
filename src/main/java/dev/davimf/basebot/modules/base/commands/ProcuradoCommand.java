package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.fun.MemeService;
import dev.davimf.basebot.modules.base.fun.MemeTemplate;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Map;

/** /procurado — cartaz "Procurado" com o avatar do alvo. */
public final class ProcuradoCommand implements SlashCommand {
    private final MemeService memes;

    public ProcuradoCommand(MemeService memes) {
        this.memes = memes;
    }

    @Override public String name() { return "procurado"; }

    @Override public SlashCommandData data() {
        return Commands.slash("procurado", "Coloca alguém num cartaz de 'Procurado'.")
                .addOption(OptionType.USER, "usuario", "Alvo (padrão: você)", false);
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        OptionMapping opt = event.getOption("usuario");
        Member alvo = opt == null ? event.getMember() : opt.getAsMember();
        if (alvo == null) {
            Replies.ephemeral(event, ctx, "Alvo inválido.");
            return;
        }
        memes.generate(event, MemeTemplate.PROCURADO, alvo,
                Map.of("title", "PROCURADO", "reward", "RECOMPENSA $10.000", "name", alvo.getEffectiveName()),
                "🤠 Procurado!");
    }
}
