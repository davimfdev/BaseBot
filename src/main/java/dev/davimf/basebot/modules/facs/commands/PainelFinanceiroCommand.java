// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.commands
// 
// Class: PainelFinanceiroCommand
// 
// Constructors:
//   - `Constructor` : `public PainelFinanceiroCommand(FinanceService service)`
// 
// Methods:
//   - `Method` : `public String name()`
//   - `Method` : `public SlashCommandData data()`
// 
// Fields:
//   - `Field` : `private final FinanceService service`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.facs.economy.FinanceService;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions.Capability;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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
        return Commands.slash("painel-financeiro", "Painel financeiro da facção.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, Capability.FINANCEIRO)) {
            Replies.ephemeral(event, ctx, "Você não tem permissão **Financeiro** para usar este painel.");
            return;
        }
        // Post as a normal channel message (editable via /mensagem editar); confirm ephemerally.
        event.getChannel().sendMessageComponents(service.panel(event.getGuild().getId())).useComponentsV2().queue(
                msg -> Replies.ephemeral(event, ctx, Emojis.of(Emojis.MONEY, "💰") + " Painel financeiro publicado."),
                err -> Replies.ephemeral(event, ctx, "Falha ao publicar: " + err.getMessage()));
    }
}
