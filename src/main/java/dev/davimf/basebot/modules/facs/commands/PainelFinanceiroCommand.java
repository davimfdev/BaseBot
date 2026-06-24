package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.facs.economy.FinanceService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /painel-financeiro — posts the faction treasury control panel (BOTSPECS Module 4). */
public final class PainelFinanceiroCommand implements SlashCommand {

    private final FinanceService service;

    public PainelFinanceiroCommand(FinanceService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "painel-financeiro";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("painel-financeiro", "Painel financeiro da facção.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        event.replyComponents(service.panel(event.getGuild().getId())).useComponentsV2().queue();
    }
}
