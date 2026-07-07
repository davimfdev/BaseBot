package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EquipmentService;
import dev.davimf.basebot.modules.base.economy.MercadoView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /mercado — vitrine efêmera de equipamentos (mineração/culinária/entrega/arma) por categoria. */
public final class MercadoCommand implements SlashCommand {
    private final EquipmentService equip;
    public MercadoCommand(EquipmentService equip) { this.equip = equip; }

    @Override public String name() { return "mercado"; }

    @Override public SlashCommandData data() {
        return Commands.slash("mercado", "Mostra os equipamentos à venda por categoria.");
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
        event.replyComponents(MercadoView.vitrine(EmbedColor.resolve(cfg), cfg))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
