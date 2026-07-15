package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.modules.base.utility.InfoView;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /banner [usuario] — mostra o banner do perfil (async). */
public final class BannerCommand implements SlashCommand {
    @Override public String name() { return "banner"; }

    @Override public SlashCommandData data() {
        return Commands.slash("banner", "Mostra o banner de alguém.")
                .addOptions(new OptionData(OptionType.USER, "usuario", "Membro (opcional)", false));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        OptionMapping o = event.getOption("usuario");
        User u = o == null ? event.getUser() : o.getAsUser();
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        event.deferReply().queue();
        u.retrieveProfile().queue(profile -> {
            String url = profile.getBannerUrl();
            if (url == null) {
                event.getHook().editOriginalComponents(Panels.container(accent,
                        Panels.text(u.getName() + " não tem banner."))).useComponentsV2().queue();
            } else {
                event.getHook().editOriginalComponents(
                                InfoView.imageWithButton(accent, "## Banner de " + u.getName(),
                                        url + "?size=2048", url + "?size=2048"))
                        .useComponentsV2().queue();
            }
        }, err -> event.getHook().editOriginalComponents(Panels.container(accent,
                Panels.text("Não consegui buscar o banner."))).useComponentsV2().queue());
    }
}
