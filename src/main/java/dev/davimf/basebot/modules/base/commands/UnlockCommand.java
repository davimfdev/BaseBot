package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /unlock — neutralizes the @everyone SEND_MESSAGES override in the current channel. */
public final class UnlockCommand implements SlashCommand {

    @Override
    public String name() {
        return "unlock";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("unlock", "Destranca o canal atual.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || !(event.getChannel() instanceof IPermissionContainer channel)) {
            Replies.ephemeral(event, ctx, "Use este comando em um canal de servidor.");
            return;
        }
        channel.upsertPermissionOverride(event.getGuild().getPublicRole())
                .clear(Permission.MESSAGE_SEND)
                .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), "/unlock"))
                .queue(
                        ok -> {
                            ctx.database().actionLogs().log(event.getGuild().getId(),
                                    event.getUser().getId(), event.getChannel().getId(), "UNLOCK", null);
                            Replies.reply(event, ctx, Emojis.of(Emojis.UNLOCK, "🔓") + " Canal destrancado.");
                        },
                        err -> Replies.ephemeral(event, ctx, "Falha: " + err.getMessage()));
    }
}
