package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.model.Punishment;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions.Capability;
import dev.davimf.basebot.modules.facs.punish.PunishService;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /punir — applies Blacklist, Demotion or ADV (warn) to a member (BOTSPECS Module 4). */
public final class PunirCommand implements SlashCommand {

    private final PunishService service;

    public PunirCommand(PunishService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "punir";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("punir", "Aplica uma punição: Blacklist, Rebaixamento ou ADV.")
                .addOption(OptionType.USER, "usuario", "Membro a punir", true)
                .addOptions(new OptionData(OptionType.STRING, "tipo", "Tipo de punição", true)
                        .addChoice("ADV (advertência)", Punishment.ADV)
                        .addChoice("Rebaixamento", Punishment.DEMOTION)
                        .addChoice("Blacklist", Punishment.BLACKLIST))
                .addOption(OptionType.STRING, "motivo", "Motivo", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, Capability.PUNICOES)) {
            Replies.ephemeral(event, ctx, "Você não tem permissão de **Punições**.");
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        if (target == null) {
            Replies.ephemeral(event, ctx, "Esse usuário não está no servidor.");
            return;
        }
        if (!Moderation.canModerate(event.getMember(), target, event.getGuild().getSelfMember())) {
            Replies.ephemeral(event, ctx, "Hierarquia insuficiente para punir esse membro.");
            return;
        }
        String tipo = event.getOption("tipo", OptionMapping::getAsString);
        String motivo = event.getOption("motivo", OptionMapping::getAsString);
        service.apply(event, target, tipo, motivo);
    }
}
