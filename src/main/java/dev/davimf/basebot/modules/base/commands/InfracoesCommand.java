package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.InfractionView;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /infrações — shows a member's full moderation case history. */
public final class InfracoesCommand implements SlashCommand {

    private final ModerationService service;

    public InfracoesCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "infrações";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("infrações", "Mostra o histórico de infrações de um membro.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Membro", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        User target = event.getOption("usuario", OptionMapping::getAsUser);
        if (target == null) {
            Replies.ephemeral(event, ctx, "Usuário inválido.");
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        event.replyComponents(InfractionView.history(accent, target.getId(),
                        service.repository().listByUser(event.getGuild().getId(), target.getId()), 0))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
