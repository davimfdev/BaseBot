// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.commands
// 
// Class: VoiceMoveCommand
// 
// Methods:
//   - `Method` : `public String name()`
//   - `Method` : `public SlashCommandData data()`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /voice-move — moves a member to another voice channel (BOTSPECS Module 1). */
public final class VoiceMoveCommand implements SlashCommand {

    @Override
    public String name() {
        return "voice-move";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("voice-move", "Move um membro para outro canal de voz.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.VOICE_MOVE_OTHERS))
                .addOption(OptionType.USER, "usuario", "Membro a mover", true)
                .addOptions(new OptionData(OptionType.CHANNEL, "canal", "Canal de voz destino", true)
                        .setChannelTypes(ChannelType.VOICE));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        OptionMapping canalOpt = event.getOption("canal");
        if (target == null || canalOpt == null) {
            Replies.ephemeral(event, ctx, "Membro ou canal inválido.");
            return;
        }
        VoiceChannel channel = canalOpt.getAsChannel().asVoiceChannel();
        if (!Moderation.canModerate(event.getMember(), target, event.getGuild().getSelfMember())) {
            Replies.ephemeral(event, ctx, "Hierarquia insuficiente.");
            return;
        }
        GuildVoiceState vs = target.getVoiceState();
        if (vs == null || !vs.inAudioChannel()) {
            Replies.ephemeral(event, ctx, "Esse membro não está em um canal de voz.");
            return;
        }
        event.getGuild().moveVoiceMember(target, channel).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "VOICE_MOVE", channel.getId());
                    Replies.reply(event, ctx, target.getUser().getAsTag() + " movido para " + channel.getName());
                },
                err -> Replies.ephemeral(event, ctx, "Falha: " + err.getMessage()));
    }
}
