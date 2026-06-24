package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.facs.punish.PunishService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /punições — view a member's punishment history and revoke active ones (BOTSPECS Module 4). */
public final class PunicoesCommand implements SlashCommand {

    private final PunishService service;

    public PunicoesCommand(PunishService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "punições";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("punições", "Histórico de punições de um membro.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Membro", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        String userId = target != null ? target.getId()
                : event.getOption("usuario", OptionMapping::getAsUser).getId();
        event.replyComponents(service.history(event.getGuild().getId(), userId))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
