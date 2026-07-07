package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EquipmentService;
import dev.davimf.basebot.modules.base.economy.InventarioView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /inventario — itens comprados pelo usuário (efêmero); equipar sempre opera por rowId. */
public final class InventarioCommand implements SlashCommand {
    private final EquipmentService equip;
    public InventarioCommand(EquipmentService equip) { this.equip = equip; }

    @Override public String name() { return "inventario"; }

    @Override public SlashCommandData data() {
        return Commands.slash("inventario", "Mostra seus equipamentos e permite equipá-los.");
    }

    @Override public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        String g = event.getGuild().getId();
        String u = event.getMember().getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(g);
        if (!EconomyConfig.enabled(cfg)) {
            Replies.ephemeral(event, ctx, "Economia desativada neste servidor.");
            return;
        }
        event.replyComponents(InventarioView.panel(EmbedColor.resolve(cfg), equip.inventory(g, u), cfg))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
