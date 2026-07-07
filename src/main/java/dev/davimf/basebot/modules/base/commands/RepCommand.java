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

/** /rep [usuario] — dá reputação (ou mostra o ranking). */
public final class RepCommand implements SlashCommand {
    @Override public String name() { return "rep"; }

    @Override public SlashCommandData data() {
        return Commands.slash("rep", "Dá reputação a alguém (ou vê o ranking).")
                .addOptions(new OptionData(OptionType.USER, "usuario", "Quem recebe (vazio = ranking)", false));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        SocialGive.handle(event, ctx, "rep", "reputação", Emojis.of(Emojis.STAR, "⭐"));
    }
}
