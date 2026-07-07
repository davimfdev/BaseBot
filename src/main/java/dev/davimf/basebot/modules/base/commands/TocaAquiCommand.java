package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.modules.base.fun.GifClient;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /toca_aqui @u — GIF de carinho (nekos.best pat). */
public final class TocaAquiCommand implements SlashCommand {
    private final GifClient gif;

    public TocaAquiCommand(GifClient gif) { this.gif = gif; }

    @Override public String name() { return "toca_aqui"; }

    @Override public SlashCommandData data() {
        return Commands.slash("toca_aqui", "Faz um carinho em alguém.")
                .addOptions(new OptionData(OptionType.USER, "usuario", "Quem recebe o carinho", true));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        User alvo = event.getOption("usuario", OptionMapping::getAsUser);
        if (alvo == null) {
            Replies.ephemeral(event, ctx, "Escolha alguém.");
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        String texto = event.getUser().getAsMention() + " fez um carinho em " + alvo.getAsMention() + " 🥰";
        event.deferReply().queue();
        gif.fetch("pat").thenAccept(url -> {
            if (url.isPresent()) {
                event.getHook().editOriginalComponents(Panels.container(accent, Panels.text(texto),
                                MediaGallery.of(MediaGalleryItem.fromUrl(url.get()))))
                        .useComponentsV2().queue(ok -> { }, err -> { });
            } else {
                event.getHook().editOriginalComponents(Panels.container(accent, Panels.text(texto)))
                        .useComponentsV2().queue(ok -> { }, err -> { });
            }
        });
    }
}
