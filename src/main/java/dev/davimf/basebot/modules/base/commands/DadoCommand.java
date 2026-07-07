package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.concurrent.ThreadLocalRandom;

/** /dado [lados] — rola um dado. */
public final class DadoCommand implements SlashCommand {
    @Override public String name() { return "dado"; }

    @Override public SlashCommandData data() {
        return Commands.slash("dado", "Rola um dado.")
                .addOptions(new OptionData(OptionType.INTEGER, "lados", "Número de lados (2–1000, default 6)", false));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        OptionMapping o = event.getOption("lados");
        long lados = o == null ? 6 : Math.max(2, Math.min(1000, o.getAsLong()));
        long roll = ThreadLocalRandom.current().nextLong(1, lados + 1);
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        event.replyComponents(Panels.container(accent, Panels.text(
                        Emojis.of(Emojis.GAME, "🎲") + " Você rolou um **d" + lados + "** e tirou **" + roll + "**!")))
                .useComponentsV2().queue();
    }
}
