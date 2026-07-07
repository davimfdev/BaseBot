package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.modules.base.fun.ShipCalc;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Emojis;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /ship @a @b — compatibilidade. */
public final class ShipCommand implements SlashCommand {
    @Override public String name() { return "ship"; }

    @Override public SlashCommandData data() {
        return Commands.slash("ship", "Calcula a compatibilidade entre duas pessoas.")
                .addOptions(new OptionData(OptionType.USER, "pessoa1", "Primeira pessoa", true))
                .addOptions(new OptionData(OptionType.USER, "pessoa2", "Segunda pessoa", true));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        User a = event.getOption("pessoa1", OptionMapping::getAsUser);
        User b = event.getOption("pessoa2", OptionMapping::getAsUser);
        if (a == null || b == null) {
            Replies.ephemeral(event, ctx, "Escolha duas pessoas.");
            return;
        }
        int p = ShipCalc.percent(a.getId(), b.getId());
        String verdict = p >= 90 ? "Casamento à vista! 💍" : p >= 60 ? "Tem química! 🔥"
                : p >= 30 ? "Talvez role… 🤔" : "Melhor como amigos. 🙃";
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        event.replyComponents(Panels.container(accent, Panels.text(
                        "## " + Emojis.of(Emojis.HEART, "💞") + " Ship\n" + a.getAsMention() + " + " + b.getAsMention()
                                + "\n" + ShipCalc.bar(p) + "\n" + verdict)))
                .useComponentsV2().queue();
    }
}
