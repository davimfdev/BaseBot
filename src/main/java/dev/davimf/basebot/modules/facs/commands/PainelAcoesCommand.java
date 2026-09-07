package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.facs.actions.ActionService;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions.Capability;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /painel-acoes — posts the action management panel (Registrar/Editar) (BOTSPECS Module 4). */
public final class PainelAcoesCommand implements SlashCommand {

    private final ActionService service;

    public PainelAcoesCommand(ActionService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "painel-acoes";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("painel-acoes", "Envia o painel de ações (registrar e editar ações).");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, Capability.ACOES)) {
            Replies.ephemeral(event, ctx, "Você não tem permissão de **Ações** para usar este painel.");
            return;
        }
        service.postManagementPanel(event);
    }
}
