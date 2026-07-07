package dev.davimf.basebot.modules.base.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.base.moderation.InfractionView;
import dev.davimf.basebot.modules.base.moderation.ModerationService;
import dev.davimf.basebot.util.EmbedColor;
import dev.davimf.basebot.util.Replies;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** /caso — shows one moderation case by its number, with a Revogar button. */
public final class CasoCommand implements SlashCommand {

    private final ModerationService service;

    public CasoCommand(ModerationService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "caso";
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("caso", "Mostra os detalhes de um caso de moderação.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.INTEGER, "numero", "Número do caso", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        long raw = event.getOption("numero", 0L, OptionMapping::getAsLong);
        int number = (int) raw;
        int accent = EmbedColor.resolve(ctx.database().guildConfig().findOrEmpty(event.getGuild().getId()));
        service.repository().findByCase(event.getGuild().getId(), number).ifPresentOrElse(
                inf -> event.replyComponents(InfractionView.caseDetail(accent, inf))
                        .useComponentsV2().setEphemeral(true).queue(),
                () -> Replies.ephemeral(event, ctx, "Caso #" + number + " não encontrado."));
    }
}
