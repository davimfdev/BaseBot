package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.ShopService;
import dev.davimf.basebot.modules.base.economy.ShopView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /loja — vitrine efêmera para comprar itens da economia. */
public final class LojaCommand implements SlashCommand {
    private final ShopService shop;
    public LojaCommand(ShopService shop) { this.shop = shop; }

    @Override public String name() { return "loja"; }

    @Override public SlashCommandData data() {
        return Commands.slash("loja", "Abre a loja da economia (cargos e itens).");
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!EconomyConfig.enabled(cfg)) {
            Replies.ephemeral(event, ctx, "Economia desativada neste servidor.");
            return;
        }
        event.replyComponents(ShopView.vitrine(EmbedColor.resolve(cfg), shop.catalog(event.getGuild()), cfg))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
