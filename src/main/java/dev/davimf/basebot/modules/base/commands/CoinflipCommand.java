package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.concurrent.ThreadLocalRandom;

/** /coinflip — cara ou coroa. */
public final class CoinflipCommand implements SlashCommand {
    @Override public String name() { return "coinflip"; }

    @Override public SlashCommandData data() {
        return Commands.slash("coinflip", "Cara ou coroa.");
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        String side = ThreadLocalRandom.current().nextBoolean() ? "Cara" : "Coroa";
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        event.replyComponents(Panels.container(accent, Panels.text(
                        Emojis.of(Emojis.CASH, "🪙") + " Deu **" + side + "**!")))
                .useComponentsV2().queue();
    }
}
