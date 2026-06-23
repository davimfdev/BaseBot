package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /mutecall — persistent server voice mute; re-applied on reconnect (BOTSPECS Module 1). */
public final class MuteCallCommand implements SlashCommand {

    @Override
    public String name() {
        return "mutecall";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("mutecall", "Silencia um membro na call (persistente).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.VOICE_MUTE_OTHERS))
                .addOption(OptionType.USER, "usuario", "Membro a silenciar", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        if (target == null) {
            event.reply("Membro inválido.").setEphemeral(true).queue();
            return;
        }
        if (!Moderation.canModerate(event.getMember(), target, event.getGuild().getSelfMember())) {
            event.reply("Hierarquia insuficiente.").setEphemeral(true).queue();
            return;
        }
        ctx.database().voiceMutes().add(event.getGuild().getId(), target.getId());
        ctx.database().actionLogs().log(event.getGuild().getId(),
                event.getUser().getId(), target.getId(), "VOICE_MUTE", null);

        GuildVoiceState vs = target.getVoiceState();
        if (vs != null && vs.inAudioChannel()) {
            event.getGuild().mute(target, true).reason("Mute de call").queue();
        }
        event.reply(target.getUser().getAsTag() + " silenciado na call (persistente).").queue();
    }
}
