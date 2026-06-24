package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.facs.economy.FarmService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /farm — submit raw materials for manager approval (BOTSPECS Module 4). */
public final class FarmCommand implements SlashCommand {

    private final FarmService service;

    public FarmCommand(FarmService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "farm";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("farm", "Entrega materiais de farm para aprovação.")
                .addOption(OptionType.STRING, "material", "Material entregue", true)
                .addOptions(new OptionData(OptionType.INTEGER, "quantidade", "Quantidade", true)
                        .setMinValue(1).setMaxValue(1_000_000));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        String item = event.getOption("material", OptionMapping::getAsString).trim();
        long qty = event.getOption("quantidade", 0L, OptionMapping::getAsLong);
        if (item.isBlank() || qty <= 0) {
            event.reply("Material ou quantidade inválidos.").setEphemeral(true).queue();
            return;
        }
        service.submit(event, item, qty);
    }
}
