package dev.davimf.basebot.modules.tickets;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.TicketCategory;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;

/** /ticket painel — posts the public panel members use to open tickets (BOTSPECS Module 2). */
public final class TicketPanelCommand implements SlashCommand {

    @Override
    public String name() {
        return "ticket";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("ticket", "Sistema de tickets.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(new SubcommandData("painel",
                        "Envia neste canal o painel para os membros abrirem tickets."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        List<TicketCategory> categories =
                ctx.database().ticketCategories().listByGuild(event.getGuild().getId());
        if (categories.isEmpty()) {
            event.reply("Nenhuma categoria de ticket configurada. Use /setup → Tickets primeiro.")
                    .setEphemeral(true).queue();
            return;
        }
        event.replyComponents(TicketView.panel(categories)).useComponentsV2().queue();
    }
}
