package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /disconnect — kicks a member from their voice channel (BOTSPECS Module 1). */
public final class DisconnectCommand implements SlashCommand {

    @Override
    public String name() {
        return "disconnect";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("disconnect", "Desconecta um membro do canal de voz.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.VOICE_MOVE_OTHERS))
                .addOption(OptionType.USER, "usuario", "Membro a desconectar", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        if (target == null) {
            Replies.ephemeral(event, ctx, "Membro inválido.");
            return;
        }
        if (!Moderation.canModerate(event.getMember(), target, event.getGuild().getSelfMember())) {
            Replies.ephemeral(event, ctx, "Hierarquia insuficiente.");
            return;
        }
        GuildVoiceState vs = target.getVoiceState();
        if (vs == null || !vs.inAudioChannel()) {
            Replies.ephemeral(event, ctx, "Esse membro não está em um canal de voz.");
            return;
        }
        event.getGuild().kickVoiceMember(target).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "VOICE_DISCONNECT", null);
                    Replies.reply(event, ctx, target.getUser().getAsTag() + " foi desconectado.");
                },
                err -> Replies.ephemeral(event, ctx, "Falha: " + err.getMessage()));
    }
}
