package dev.davimf.basebot.modules.tickets;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.TicketCategory;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
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
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        String guildId = event.getGuild().getId();
        List<TicketCategory> categories = ctx.database().ticketCategories().listByGuild(guildId);
        if (categories.isEmpty()) {
            Replies.ephemeral(event, ctx, "Nenhuma categoria de ticket configurada. Use /setup → Tickets primeiro.");
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(guildId));
        // Post as a normal channel message (editable via /mensagem editar); confirm ephemerally.
        event.getChannel().sendMessageComponents(TicketView.panel(accent, categories)).useComponentsV2().queue(
                msg -> Replies.ephemeral(event, ctx, Emojis.of(Emojis.TICKET, "🎟️") + " Painel de tickets publicado."),
                err -> Replies.ephemeral(event, ctx, "Falha ao publicar: " + err.getMessage()));
    }
}
