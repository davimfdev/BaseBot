package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.economy.EconomyConfig;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentService;
import dev.davimf.basebot.modules.base.economy.InventoryRepository.Row;
import dev.davimf.basebot.modules.base.economy.RepairPolicy;
import dev.davimf.basebot.modules.base.economy.RepararView;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.ArrayList;
import java.util.List;

/** /economia reparar — repara itens quase quebrados (metade do preço, 75% dos usos, até 3x). */
public final class RepararCommand implements SlashCommand {
    private final EquipmentService equipment;
    public RepararCommand(EquipmentService equipment) { this.equipment = equipment; }

    @Override public String name() { return "reparar"; }
    @Override public SlashCommandData data() { return Commands.slash("reparar", "Repara um item quase quebrado do seu inventário."); }

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
        List<Row> repairable = new ArrayList<>();
        for (Row row : equipment.inventory(g, u)) {
            Equip e = EquipmentCatalog.byKey(row.itemKey());
            if (e != null && RepairPolicy.eligible(row.usosLeft(), e.maxUsos(), row.repairs())) {
                repairable.add(row);
            }
        }
        event.replyComponents(RepararView.panel(EmbedColor.resolve(cfg), repairable, cfg))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
