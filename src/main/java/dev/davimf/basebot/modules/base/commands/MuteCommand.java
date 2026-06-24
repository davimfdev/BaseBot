package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.modules.base.voice.MuteRepository;
import dev.davimf.basebot.util.Durations;
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
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        Member target = event.getOption("usuario", OptionMapping::getAsMember);
        if (target == null) {
            event.reply("Membro inválido.").setEphemeral(true).queue();
            return;
        }
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        String roleId = cfg.role("mutado");
        Role role = roleId == null ? null : event.getGuild().getRoleById(roleId);
        if (role == null) {
            event.reply("Cargo de mutado não configurado. Use /setup → Cargos.")
                    .setEphemeral(true).queue();
            return;
        }
        Member self = event.getGuild().getSelfMember();
        if (!Moderation.canModerate(event.getMember(), target, self) || !self.canInteract(role)) {
            event.reply("Hierarquia insuficiente para aplicar o cargo de mutado.")
                    .setEphemeral(true).queue();
            return;
        }
        var duration = Durations.parse(event.getOption("tempo", OptionMapping::getAsString));
        if (duration.isEmpty()) {
            event.reply("Tempo inválido. Use algo como `10m`, `1h` ou `1d`.").setEphemeral(true).queue();
            return;
        }
        long millis = duration.getAsLong();
        String reason = event.getOption("motivo", "Sem motivo informado.", OptionMapping::getAsString);
        String guildId = event.getGuild().getId();
        event.getGuild().addRoleToMember(target, role).reason(reason).queue(
                ok -> {
                    ctx.database().mutes().add(guildId, target.getId(), MuteRepository.TEXT,
                            System.currentTimeMillis() + millis);
                    ctx.database().actionLogs().log(guildId,
                            event.getUser().getId(), target.getId(), "MUTE", reason);
                    event.reply(target.getUser().getAsTag() + " foi silenciado por "
                            + Durations.format(millis) + ".").queue();
                },
                err -> event.reply("Falha: " + err.getMessage()).setEphemeral(true).queue());
    }
}
