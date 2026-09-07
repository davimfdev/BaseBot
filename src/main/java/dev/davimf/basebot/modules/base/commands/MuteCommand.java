package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.InfractionType;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
import dev.davimf.basebot.modules.base.voice.MuteRepository;
import dev.davimf.basebot.util.Durations;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /mute — text mute by applying the configured "mutado" role (BOTSPECS Module 1). */
public final class MuteCommand implements SlashCommand {

    private final ModerationService service;

    public MuteCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "mute";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("mute", "Silencia um membro no texto (cargo de mutado) por um tempo.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Membro a silenciar", true)
                .addOption(OptionType.STRING, "tempo", "Duração do mute (ex: 10m, 1h, 1d)", true)
                .addOption(OptionType.STRING, "motivo", "Motivo", false);
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
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        String roleId = cfg.role("mutado");
        Role role = roleId == null ? null : event.getGuild().getRoleById(roleId);
        if (role == null) {
            Replies.ephemeral(event, ctx, "Cargo de mutado não configurado. Use /setup → Cargos.");
            return;
        }
        Member self = event.getGuild().getSelfMember();
        if (!Moderation.canModerate(event.getMember(), target, self) || !self.canInteract(role)) {
            Replies.ephemeral(event, ctx, "Hierarquia insuficiente para aplicar o cargo de mutado.");
            return;
        }
        var duration = Durations.parse(event.getOption("tempo", OptionMapping::getAsString));
        if (duration.isEmpty()) {
            Replies.ephemeral(event, ctx, "Tempo inválido. Use algo como `10m`, `1h` ou `1d`.");
            return;
        }
        long millis = duration.getAsLong();
        String reason = event.getOption("motivo", "Sem motivo informado.", OptionMapping::getAsString);
        String guildId = event.getGuild().getId();
        event.getGuild().addRoleToMember(target, role)
                .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), reason)).queue(
                ok -> {
                    long expiresAt = System.currentTimeMillis() + millis;
                    ctx.database().mutes().add(guildId, target.getId(), MuteRepository.TEXT, expiresAt);
                    var c = service.record(event.getGuild(), InfractionType.MUTE, target.getUser(),
                            event.getUser().getId(), reason, expiresAt, millis);
                    Replies.reply(event, ctx, "" + Emojis.of(Emojis.MUTE, "🔇") + " " + target.getUser().getAsTag() + " foi silenciado por "
                            + Durations.format(millis) + " — Caso #" + c.caseNumber() + ".");
                },
                err -> Replies.ephemeral(event, ctx, "Falha: " + err.getMessage()));
    }
}
