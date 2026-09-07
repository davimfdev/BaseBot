package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.InfractionType;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
import dev.davimf.basebot.modules.base.voice.MuteRepository;
import dev.davimf.basebot.util.Durations;
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

/** /mutecall — persistent server voice mute; re-applied on reconnect (BOTSPECS Module 1). */
public final class MuteCallCommand implements SlashCommand {

    private final ModerationService service;

    public MuteCallCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "mutecall";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("mutecall", "Silencia um membro na call por um tempo (persistente).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.VOICE_MUTE_OTHERS))
                .addOption(OptionType.USER, "usuario", "Membro a silenciar", true)
                .addOption(OptionType.STRING, "tempo", "Duração do mute (ex: 10m, 1h, 1d)", true);
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
        var duration = Durations.parse(event.getOption("tempo", OptionMapping::getAsString));
        if (duration.isEmpty()) {
            Replies.ephemeral(event, ctx, "Tempo inválido. Use algo como `10m`, `1h` ou `1d`.");
            return;
        }
        long millis = duration.getAsLong();
        long expiresAt = System.currentTimeMillis() + millis;
        ctx.database().mutes().add(event.getGuild().getId(), target.getId(), MuteRepository.VOICE, expiresAt);
        var c = service.record(event.getGuild(), InfractionType.MUTECALL, target.getUser(),
                event.getUser().getId(), null, expiresAt, millis);

        GuildVoiceState vs = target.getVoiceState();
        if (vs != null && vs.inAudioChannel()) {
            event.getGuild().mute(target, true)
                    .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), "Mute de call")).queue();
        }
        Replies.reply(event, ctx, "" + Emojis.of(Emojis.VOLUME, "🔊") + " " + target.getUser().getAsTag() + " silenciado na call por "
                + Durations.format(millis) + " — Caso #" + c.caseNumber() + ".");
    }
}
