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
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/**
 * {@code /interagir <ação> @u} — beijo, abraço, soco, peteco, etc., cada um com GIF do nekos.best.
 *
 * <p>É um único comando com um subcomando por ação (do {@link GifInteractions#CATALOG}). Fica assim,
 * e não como 12 comandos separados, porque o Discord limita 100 comandos por servidor e o bot já
 * está perto do teto — um comando com subcomandos ocupa <b>um</b> slot.
 *
 * <p>O alvo é obrigatório e não pode ser você mesmo (regra dos comandos de fun).
 */
public final class InteragirCommand implements SlashCommand {

    private final GifClient gif;

    public InteragirCommand(GifClient gif) { this.gif = gif; }

    @Override public String name() { return "interagir"; }

    @Override public SlashCommandData data() {
        SlashCommandData cmd = Commands.slash("interagir", "Interaja com alguém: beijo, abraço, soco e mais.");
        for (GifInteractions.Spec spec : GifInteractions.CATALOG) {
            cmd.addSubcommands(new SubcommandData(spec.name(), spec.description())
                    .addOption(OptionType.USER, "usuario", "Quem recebe a ação", true));
        }
        return cmd;
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        GifInteractions.Spec spec = GifInteractions.byName(event.getSubcommandName());
        if (spec == null) {
            Replies.ephemeral(event, ctx, "Ação desconhecida.");
            return;
        }
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
