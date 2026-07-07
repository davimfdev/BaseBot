// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.commands
// 
// Class: UnmuteCommand
// 
// Methods:
//   - `Method` : `public String name()`
//   - `Method` : `public SlashCommandData data()`
// [OUTLINE END]



package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.base.moderation.InfractionType;
import dev.davimf.basebot.modules.base.moderation.Moderation;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
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

/** /unmute — removes the configured "mutado" role (BOTSPECS Module 1). */
public final class UnmuteCommand implements SlashCommand {

    private final ModerationService service;

    public UnmuteCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "unmute";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("unmute", "Remove o silêncio de texto de um membro.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
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
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        String roleId = cfg.role("mutado");
        Role role = roleId == null ? null : event.getGuild().getRoleById(roleId);
        if (role == null) {
            Replies.ephemeral(event, ctx, "Cargo de mutado não configurado. Use /setup → Cargos.");
            return;
        }
        Member self = event.getGuild().getSelfMember();
        if (!Moderation.canModerate(event.getMember(), target, self) || !self.canInteract(role)) {
            Replies.ephemeral(event, ctx, "Hierarquia insuficiente.");
            return;
        }
        event.getGuild().removeRoleFromMember(target, role)
                .reason(dev.davimf.basebot.util.ModReason.of(event.getUser(), "Unmute")).queue(
                ok -> {
                    ctx.database().mutes().remove(event.getGuild().getId(), target.getId(),
                            dev.davimf.basebot.modules.base.voice.MuteRepository.TEXT);
                    ctx.database().actionLogs().log(event.getGuild().getId(),
                            event.getUser().getId(), target.getId(), "UNMUTE", null);
                    service.deactivateLatest(event.getGuild().getId(), target.getId(), InfractionType.MUTE);
                    Replies.reply(event, ctx, target.getUser().getAsTag() + " foi dessilenciado.");
                },
                err -> Replies.ephemeral(event, ctx, "Falha: " + err.getMessage()));
    }
}
