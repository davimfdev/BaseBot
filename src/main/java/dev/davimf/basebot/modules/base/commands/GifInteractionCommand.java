package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.core.component.Panels;
import dev.davimf.basebot.modules.base.fun.GifClient;
import dev.davimf.basebot.modules.base.fun.GifInteractions;
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

/**
 * Comando de interação com GIF (nekos.best), parametrizado por uma {@link GifInteractions.Spec}.
 * Um mesmo código serve /abracar, /beijo, /soco, etc. — só muda a spec no {@code BaseModule}.
 *
 * <p>O alvo é obrigatório e não pode ser você mesmo (regra dos comandos de fun).
 */
public final class GifInteractionCommand implements SlashCommand {

    private final GifInteractions.Spec spec;
    private final GifClient gif;

    public GifInteractionCommand(GifInteractions.Spec spec, GifClient gif) {
        this.spec = spec;
        this.gif = gif;
    }

    @Override public String name() { return spec.name(); }

    @Override public SlashCommandData data() {
        return Commands.slash(spec.name(), spec.description())
                .addOptions(new OptionData(OptionType.USER, "usuario", "Quem recebe a ação", true));
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        User alvo = event.getOption("usuario", OptionMapping::getAsUser);
        if (alvo == null) {
            Replies.ephemeral(event, ctx, "Escolha alguém.");
            return;
        }
        if (alvo.getId().equals(event.getUser().getId())) {
            Replies.ephemeral(event, ctx, "Você não pode usar isso em si mesmo. 😅");
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig()
                .findOrEmpty(event.getGuild() == null ? "0" : event.getGuild().getId()));
        String texto = spec.message(event.getUser().getAsMention(), alvo.getAsMention());
        event.deferReply().queue();
        gif.fetch(spec.category()).thenAccept(url -> {
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
