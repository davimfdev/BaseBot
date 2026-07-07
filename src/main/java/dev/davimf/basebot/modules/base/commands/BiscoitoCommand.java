package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.fun.SocialGive;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /biscoito [usuario] — dá um biscoito (ou mostra o ranking). */
public final class BiscoitoCommand implements SlashCommand {
    @Override public String name() { return "biscoito"; }

    @Override public SlashCommandData data() {
        return Commands.slash("biscoito", "Dá um biscoito a alguém (ou vê o ranking).")
                .addOptions(new OptionData(OptionType.USER, "usuario", "Quem recebe (vazio = ranking)", false));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        SocialGive.handle(event, ctx, "cookie", "biscoitos", Emojis.of(Emojis.GIFT, "🍪"));
    }
}
