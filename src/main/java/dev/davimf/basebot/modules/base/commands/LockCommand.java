package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /lock — denies @everyone SEND_MESSAGES in the current channel (BOTSPECS Module 1). */
public final class LockCommand implements SlashCommand {

    @Override
    public String name() {
        return "lock";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("lock", "Tranca o canal atual (impede @everyone de enviar mensagens).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || !(event.getChannel() instanceof IPermissionContainer channel)) {
            event.reply("Use este comando em um canal de servidor.").setEphemeral(true).queue();
            return;
        }
        channel.upsertPermissionOverride(event.getGuild().getPublicRole())
                .deny(Permission.MESSAGE_SEND)
                .reason("/lock por " + event.getUser().getAsTag())
                .queue(
                        ok -> {
                            ctx.database().actionLogs().log(event.getGuild().getId(),
                                    event.getUser().getId(), event.getChannel().getId(), "LOCK", null);
                            event.reply("🔒 Canal trancado.").queue();
                        },
                        err -> event.reply("Falha: " + err.getMessage()).setEphemeral(true).queue());
    }
}
