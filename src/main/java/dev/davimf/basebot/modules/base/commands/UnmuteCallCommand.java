// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.commands
// 
// Class: UnmuteCallCommand
// 
// Methods:
//   - `Method` : `public String name()`
//   - `Method` : `public SlashCommandData data()`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.InfractionType;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
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

/** /unmutecall — clears a persistent call mute (BOTSPECS Module 1). */
public final class UnmuteCallCommand implements SlashCommand {

    private final ModerationService service;

    public UnmuteCallCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "unmutecall";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("unmutecall", "Remove o silêncio de call de um membro.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.VOICE_MUTE_OTHERS))
                .addOption(OptionType.USER, "usuario", "Membro", true);
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
        ctx.database().mutes().remove(event.getGuild().getId(), target.getId(),
                dev.davimf.basebot.modules.base.voice.MuteRepository.VOICE);
        ctx.database().actionLogs().log(event.getGuild().getId(),
                event.getUser().getId(), target.getId(), "VOICE_UNMUTE", null);
        service.deactivateLatest(event.getGuild().getId(), target.getId(), InfractionType.MUTECALL);

        GuildVoiceState vs = target.getVoiceState();
        if (vs != null && vs.inAudioChannel()) {
            event.getGuild().mute(target, false)
                    .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), "Unmute de call")).queue();
        }
        Replies.reply(event, ctx, target.getUser().getAsTag() + " liberado na call.");
    }
}
