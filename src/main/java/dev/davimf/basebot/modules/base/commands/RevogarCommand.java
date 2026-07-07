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

/** /revogar — pick which of a member's active infractions to revoke from a list. */
public final class RevogarCommand implements SlashCommand {

    private final ModerationService service;

    public RevogarCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "revogar";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("revogar", "Revoga uma infração de um membro (escolha numa lista).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "membro", "Membro", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        User target = event.getOption("membro", OptionMapping::getAsUser);
        if (target == null) {
            Replies.ephemeral(event, ctx, "Usuário inválido.");
            return;
        }
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        event.replyComponents(InfractionView.revokePicker(accent, target.getId(),
                        service.repository().listActiveByUser(event.getGuild().getId(), target.getId())))
                .useComponentsV2().setEphemeral(true).queue();
    }
}
