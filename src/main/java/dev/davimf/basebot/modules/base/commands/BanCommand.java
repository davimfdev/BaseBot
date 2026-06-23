package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.concurrent.TimeUnit;

/** /ban — bans a user (works by ID even if they left), validating hierarchy when present. */
public final class BanCommand implements SlashCommand {

    @Override
    public String name() {
        return "ban";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("ban", "Bane um usuário do servidor.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Usuário a banir", true)
                .addOption(OptionType.STRING, "motivo", "Motivo", false)
                .addOption(OptionType.INTEGER, "dias", "Dias de mensagens a apagar (0-7)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        User target = event.getOption("usuario", OptionMapping::getAsUser);
        if (target == null) {
            event.reply("Usuário inválido.").setEphemeral(true).queue();
            return;
        }
        Member targetMember = event.getOption("usuario", OptionMapping::getAsMember);
        Member self = event.getGuild().getSelfMember();
        if (targetMember != null && !Moderation.canModerate(event.getMember(), targetMember, self)) {
            event.reply("Hierarquia insuficiente: você ou o bot não estão acima desse membro.")
                    .setEphemeral(true).queue();
            return;
        }
        String reason = event.getOption("motivo", "Sem motivo informado.", OptionMapping::getAsString);
        long days = event.getOption("dias", 0L, OptionMapping::getAsLong);
        int clamped = (int) Math.max(0, Math.min(7, days));

        event.getGuild().ban(target, clamped, TimeUnit.DAYS).reason(reason).queue(
                ok -> {
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "BAN", reason);
                    event.reply("Usuário banido: " + target.getAsTag()).queue();
                },
                err -> event.reply("Falha ao banir: " + err.getMessage()).setEphemeral(true).queue());
    }
}
