package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.Infraction;
import dev.davimf.basebot.modules.base.moderation.InfractionType;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /nota — adds an internal moderator note to a user (never DMed, never escalates). */
public final class NotaCommand implements SlashCommand {

    private final ModerationService service;

    public NotaCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "nota";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("nota", "Adiciona uma nota interna sobre um membro (só a equipe vê).")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "usuario", "Membro", true)
                .addOption(OptionType.STRING, "texto", "Conteúdo da nota", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        User target = event.getOption("usuario", OptionMapping::getAsUser);
        String texto = event.getOption("texto", OptionMapping::getAsString);
        if (target == null || texto == null || texto.isBlank()) {
            Replies.ephemeral(event, ctx, "Informe um membro e o texto da nota.");
            return;
        }
        Infraction c = service.record(event.getGuild(), InfractionType.NOTE, target,
                event.getUser().getId(), texto, null, null);
        Replies.ephemeral(event, ctx, "" + Emojis.of(Emojis.NOTE, "📝") + " Nota adicionada a " + target.getAsMention() + " — Caso #" + c.caseNumber() + ".");
    }
}
