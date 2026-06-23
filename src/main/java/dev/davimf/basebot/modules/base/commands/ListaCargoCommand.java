package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.listacargo.ListaCargoView;
import dev.davimf.basebot.util.Paginator;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/** /listacargo — paginated list of members holding a role (BOTSPECS Module 1). */
public final class ListaCargoCommand implements SlashCommand {

    @Override
    public String name() {
        return "listacargo";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("listacargo", "Lista os membros de um cargo (paginado).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
                .addOption(OptionType.ROLE, "cargo", "Cargo a listar", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Role role = event.getOption("cargo", OptionMapping::getAsRole);
        if (role == null) {
            event.reply("Cargo inválido.").setEphemeral(true).queue();
            return;
        }
        List<Member> members = event.getGuild().getMembersWithRoles(role);
        int pages = Paginator.pageCount(members.size(), ListaCargoView.PAGE_SIZE);
        event.replyEmbeds(ListaCargoView.embed(role, members, 0))
                .addComponents(ListaCargoView.navRow(role.getId(), 0, pages))
                .queue();
    }
}
