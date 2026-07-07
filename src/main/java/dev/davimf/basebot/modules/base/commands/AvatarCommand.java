package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.utility.InfoView;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /avatar [usuario] — mostra o avatar em tamanho grande. */
public final class AvatarCommand implements SlashCommand {
    @Override public String name() { return "avatar"; }

    @Override public SlashCommandData data() {
        return Commands.slash("avatar", "Mostra o avatar de alguém.")
                .addOptions(new OptionData(OptionType.USER, "usuario", "Membro (opcional)", false));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        OptionMapping o = event.getOption("usuario");
        User u = o == null ? event.getUser() : o.getAsUser();
        String url = u.getEffectiveAvatarUrl() + "?size=1024";
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        event.replyComponents(InfoView.imageWithButton(accent, "## Avatar de " + u.getName(), url, url))
                .useComponentsV2().queue();
    }
}
